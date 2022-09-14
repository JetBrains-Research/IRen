package org.jetbrains.iren.ngram;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.counting.trie.MapTrieCounter;
import com.intellij.completion.ngram.slp.modeling.Model;
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.util.io.IOUtil;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.services.IRenSuggestingService;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.storages.Vocabulary;
import org.jetbrains.iren.storages.VocabularyRunner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class NGramModelRunner implements ModelRunner {
    protected final static String COUNTER_FILE = "counter.ser";
    protected final static String FORWARD_COUNTER_FILE = "forwardCounter.ser";
    protected final static String REVERSE_COUNTER_FILE = "reverseCounter.ser";
    protected final static String REMEMBER_IDENTIFIERS_FILE = "rememberedIdentifiers.json";
    public static long CACHE_SIZE = 1024L;
    public static boolean DEFAULT_BIDIRECTIONAL = true;
    /**
     * {@link Set} of identifier names.
     */
    protected final IntOpenHashSet myRememberedIdentifiers;
    protected final Model myModel;
    protected Vocabulary myVocabulary;
    protected final boolean biDirectional;
    protected final int order;
    protected boolean myTraining = false;
    protected LanguageSupporter mySupporter = null;
    private PsiNameIdentifierOwner lastVariable = null;
    private Context<Integer> lastContext = null;

    public String getVocabularyFile() {
        return "vocabulary.txt";
    }

    public NGramModelRunner() {
        this(DEFAULT_BIDIRECTIONAL, 6);
    }

    public NGramModelRunner(boolean biDirectional, int order) {
        this(biDirectional ?
                        new BiDirectionalModel(new JMModel(order, 0.5, new MapTrieCounter()),
                                new JMModel(order, 0.5, new MapTrieCounter())) :
                        new JMModel(order, 0.5, new MapTrieCounter()),
                new Vocabulary(),
                new IntOpenHashSet(),
                biDirectional,
                order);
    }

    public NGramModelRunner(Model model, Vocabulary vocabulary, Set<Integer> rememberedIdentifiers, boolean biDirectional, int order) {
        myModel = model;
        myVocabulary = vocabulary;
        myRememberedIdentifiers = new IntOpenHashSet(rememberedIdentifiers);
        this.biDirectional = biDirectional;
        this.order = order;
    }

    public Set<Integer> getRememberedIdentifiers() {
        return myRememberedIdentifiers;
    }

    public Model getModel() {
        return myModel;
    }

    public Vocabulary getVocabulary() {
        return myVocabulary;
    }

    public void train() {
        myTraining = true;
    }

    public void eval() {
        myTraining = false;
    }

    private final LoadingCache<SmartPsiElementPointer<PsiNameIdentifierOwner>, List<VarNamePrediction>> cache =
            CacheBuilder.newBuilder()
                    .maximumSize(CACHE_SIZE)
                    .build(new CacheLoader<>() {
                               @Override
                               public @NotNull List<VarNamePrediction> load(@NotNull SmartPsiElementPointer<PsiNameIdentifierOwner> variable) {
                                   PsiNameIdentifierOwner element = ReadAction.compute(variable::getElement);
                                   if (element == null) return List.of();
                                   @Nullable Context<Integer> intContext = getContext(element);
                                   return intContext == null ? List.of() : suggestNames(intContext);
                               }
                           }
                    );

    @Override
    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable) {
        try {
//            Use smart pointers here because after renaming PsiElement is replaced with the new one and caching doesn't work.
            return cache.get(ReadAction.compute(() -> SmartPointerManager.createPointer(variable)));
        } catch (ExecutionException ignore) {
            return List.of();
        }
    }

    @NotNull
    public List<VarNamePrediction> suggestNames(@NotNull Context<Integer> intContext) {
        Context<Integer> unknownContext = intContext.with(0);
        Set<Integer> candidates = new HashSet<>();
        for (int idx : intContext.getVarIdxs()) {
            candidates.addAll(getCandidates(unknownContext.getTokens(), idx));
        }
        return rankCandidates(candidates, unknownContext);
    }

    @Override
    public Context.@NotNull Statistics getContextStatistics(@NotNull PsiNameIdentifierOwner variable) {
        @Nullable Context<Integer> intContext = getContext(variable);
        return intContext == null ? Context.Statistics.EMPTY : getContextStatistics(intContext);
    }

    @NotNull
    private Context.Statistics getContextStatistics(@NotNull Context<Integer> intContext) {
        Context<Integer> unknownContext = intContext.with(0);
        int usageNumber = intContext.getVarIdxs().size();
        int countsSum = 0;
        for (int idx : intContext.getVarIdxs()) {
            countsSum += getContextCount(unknownContext.getTokens(), idx);
        }
        return new Context.Statistics(usageNumber, countsSum);
    }

    private int getContextCount(List<Integer> tokens, int index) {
        List<Integer> forward = tokens.subList(max(0, index - getOrder() + 1), index + 1);
        if (myModel instanceof BiDirectionalModel) {
            List<Integer> reverse = tokens.subList(index, min(index + getOrder(), tokens.size()));
            Collections.reverse(reverse);
            final Counter forwardCounter = ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter();
            final Counter reverseCounter = ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter();
            return (int) (forwardCounter.getCounts(forward)[1] + reverseCounter.getCounts(reverse)[1]);
        } else {
            return (int) ((NGramModel) myModel).getCounter().getCounts(forward)[1];
        }
    }

    @Override
    public @NotNull Pair<Double, Double> getProbability(PsiNameIdentifierOwner variable) {
        @Nullable Context<Integer> intContext = getContext(variable);
        return intContext == null ? new Pair<>(0., 0.) : new Pair<>(getProbability(intContext), getModelPriority());
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public double getModelPriority() {
        return 1;
    }

    @Override
    public void learnPsiFile(@NotNull PsiFile file) {
        LanguageSupporter supporter = getSupporter(file);
        if (supporter == null) return;
        @NotNull List<String> lexed = supporter.lexPsiFile(file, myTraining ? rememberIdName(supporter) : null);
        learnLexed(lexed);
    }

    private Consumer<PsiElement> rememberIdName(LanguageSupporter supporter) {
        return (PsiElement element) -> {
            if (supporter.identifierIsVariableDeclaration(element)) {
                synchronized (this) {
                    myRememberedIdentifiers.add(myVocabulary.toIndex(element.getText()));
                }
            }
        };
    }

    protected synchronized void learnLexed(List<String> lexed) {
        List<Integer> indices = myVocabulary.toIndices(lexed);
        if (myVocabulary.getWordIndices().size() != myVocabulary.getWords().size()) {
            throw new AssertionError("Something went wrong with vocabulary!");
        }
        myModel.learn(indices);
    }

    @Override
    public void forgetPsiFile(@NotNull PsiFile file) {
        final LanguageSupporter supporter = getSupporter(file);
        if (supporter == null) return;
        myModel.forget(myVocabulary.toIndices(supporter.lexPsiFile(file)));
    }

    @Override
    public double save(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        modelPath.toFile().mkdirs();
        long counterSize = saveCounters(modelPath, progressIndicator);
        final long rememberedVariablesFileSize = saveRememberedVariable(modelPath, progressIndicator);
        if (rememberedVariablesFileSize < 0) return -1;
        long vocabularySize = saveVocabulary(modelPath, progressIndicator);
        if (vocabularySize < 0) return -1;
        return (counterSize + vocabularySize + rememberedVariablesFileSize) / (1024. * 1024);
    }

    private long saveVocabulary(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        File vocabularyFile = modelPath.resolve(getVocabularyFile()).toFile();
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return -1;
            progressIndicator.setText2(IRenBundle.message("saving.file", vocabularyFile.getName()));
        }
        try {
            if (vocabularyFile.exists()) IOUtil.deleteAllFilesStartingWith(vocabularyFile);
            saveVocabulary(vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return vocabularyFile.length();
    }

    protected void saveVocabulary(File file) throws IOException {
        VocabularyRunner.INSTANCE.write(myVocabulary, file);
    }

    private long saveRememberedVariable(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        File rememberedVariablesFile = modelPath.resolve(REMEMBER_IDENTIFIERS_FILE).toFile();
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return -1;
            progressIndicator.setText2(IRenBundle.message("saving.file", rememberedVariablesFile.getName()));
        }
        try {
            rememberedVariablesFile.createNewFile();
            Gson gson = new GsonBuilder().create();
            try (FileOutputStream fileOutputStream = new FileOutputStream(rememberedVariablesFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(myRememberedIdentifiers));
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return rememberedVariablesFile.length();
    }

    protected long saveCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        if (biDirectional) {
            File forwardCounterFile = modelPath.resolve(FORWARD_COUNTER_FILE).toFile();
            long counterSize1 = saveCounter(forwardCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter(),
                    progressIndicator);
            if (counterSize1 < 0) return counterSize1;
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            long counterSize2 = saveCounter(reverseCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter(),
                    progressIndicator);
            if (counterSize2 < 0) return counterSize2;
            return counterSize1 + counterSize2;
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            return saveCounter(counterFile,
                    ((NGramModel) myModel).getCounter(),
                    progressIndicator);
        }
    }

    protected long saveCounter(@NotNull File file, @NotNull Counter counter, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return -1;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2(IRenBundle.message("saving.file", file.getName()));
        }
        try {
            file.createNewFile();
            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                counter.writeExternal(objectOutputStream);
                return file.length();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    public boolean load(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        File rememberedVariablesFile = modelPath.resolve(REMEMBER_IDENTIFIERS_FILE).toFile();
        File vocabularyFile = modelPath.resolve(getVocabularyFile()).toFile();
        return rememberedVariablesFile.exists() &&
                vocabularyFile.exists() &&
                loadCounters(modelPath, progressIndicator) &&
                loadIndicesToRemember(progressIndicator, rememberedVariablesFile) &&
                loadVocabulary(progressIndicator, vocabularyFile);
    }

    public boolean loadCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        if (biDirectional) {
            File forwardCounterFile = modelPath.resolve(FORWARD_COUNTER_FILE).toFile();
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            return forwardCounterFile.exists() && reverseCounterFile.exists() &&
                    loadCounter(forwardCounterFile,
                            ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter(),
                            progressIndicator) &&
                    loadCounter(reverseCounterFile,
                            ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter(),
                            progressIndicator);
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            return counterFile.exists() && loadCounter(counterFile,
                    ((NGramModel) myModel).getCounter(),
                    progressIndicator);
        }
    }

    protected boolean loadCounter(File counterFile, Counter counter, ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2(IRenBundle.message("loading.file", counterFile.getName()));
        }
        try (FileInputStream fileInputStream = new FileInputStream(counterFile);
             ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
            counter.readExternal(objectInputStream);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    protected boolean loadVocabulary(@Nullable ProgressIndicator progressIndicator, File vocabularyFile) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setText2(IRenBundle.message("loading.file", vocabularyFile.getName()));
        }
        myVocabulary = loadVocabulary(vocabularyFile);
        return true;
    }

    protected Vocabulary loadVocabulary(File file) {
        return VocabularyRunner.INSTANCE.read(file);
    }

    private boolean loadIndicesToRemember(@Nullable ProgressIndicator progressIndicator, File rememberedVariablesFile) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setText2(IRenBundle.message("loading.file", rememberedVariablesFile.getName()));
        }
        try {
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader(rememberedVariablesFile));
            indicesToRemember(gson.fromJson(reader, HashSet.class));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void indicesToRemember(@NotNull Collection<Double> fromJson) {
        for (Double id : fromJson) {
            myRememberedIdentifiers.add(id.intValue());
        }
    }

    @Nullable
    synchronized public Context<Integer> getContext(@NotNull PsiNameIdentifierOwner variable) {
        if (lastVariable == null || !lastVariable.equals(variable)) {
            lastVariable = variable;
            lastContext = prepareContext(variable);
        }
        return lastContext;
    }

    private @Nullable Context<Integer> prepareContext(PsiNameIdentifierOwner variable) {
        final LanguageSupporter supporter = getSupporter(variable);
        if (supporter == null) return null;
        final Context<String> context = supporter.getContext(variable, false);
        if (context == null) return null;
        return Context.fromStringToInt(context, myVocabulary);
    }

    private @Nullable LanguageSupporter getSupporter(PsiElement element) {
//        TODO: initialize mySupporter with the model itself.
//         It looks horrible, I will fix it later.
//         It needs a lot of rewriting.
//         Mb models should be stored in project_hash_version/language
        if (mySupporter == null) {
            mySupporter = LanguageSupporter.getInstance(element.getLanguage());
        }
        return mySupporter != null && mySupporter.getLanguage().equals(element.getLanguage()) ? mySupporter : null;
    }

    private @NotNull Set<Integer> getCandidates(@NotNull List<Integer> tokenIdxs, int idx) {
        return myModel.predictToken(tokenIdxs, idx).keySet();
    }

    private @NotNull List<VarNamePrediction> rankCandidates(@NotNull Set<Integer> candidates,
                                                            @NotNull Context<Integer> intContext) {
        List<Integer> cs = new ArrayList<>();
        List<Double> logits = new ArrayList<>();
        for (int candidate : candidates) {
            cs.add(candidate);
            logits.add(getProbability(intContext.with(candidate)));
            if (isCanceled()) break;
        }
//        List<Double> logProbs = logits;
        List<Double> probs = softmax(logits, 6);
        List<VarNamePrediction> predictions = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            String name = myVocabulary.toWord(cs.get(i));
            if (mySupporter.isStopName(name)) continue;
            predictions.add(new VarNamePrediction(name, probs.get(i), ModelType.NGRAM, getModelPriority()));
        }
        predictions.sort((a, b) -> -Double.compare(a.getProbability(), b.getProbability()));
        return predictions.subList(0, getCutOff(predictions));
    }

    private boolean isCanceled() {
        try {
            ProgressManager.checkCanceled();
            return false;
        } catch (ProcessCanceledException ignore) {
            return true;
        }
    }

    private static int getCutOff(List<VarNamePrediction> predictions) {
        int maxSize = min(predictions.size(), IRenSuggestingService.PREDICTION_CUTOFF);
        for (int i = 0; i < maxSize; i++) {
            if (Vocabulary.unknownCharacter.equals(predictions.get(i).getName())) return i;
        }
        return maxSize;
    }

    private double getProbability(@NotNull Context<Integer> intContext) {
        double logProb = 0.;
        int leftIdx;
        int rightIdx = 0;
        List<Integer> tokens = intContext.getTokens();
        final int maxIdx = tokens.size();
        for (int idx : intContext.getVarIdxs()) {
            leftIdx = max(biDirectional ? idx - getOrder() + 1 : idx, rightIdx);
            rightIdx = min(idx + getOrder(), maxIdx);
            for (int i = leftIdx; i < rightIdx; i++) {
                logProb += log(toProb(myModel.modelToken(tokens, i)));
            }
        }
        return logProb;
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }

    private static @NotNull List<Double> softmax(@NotNull List<Double> logits, double temperature) {
        if (logits.isEmpty()) return logits;
        List<Double> logits_t = logits.stream().map(l -> l / temperature).collect(Collectors.toList());
        Double maxLogit = Collections.max(logits_t);
        List<Double> probs = logits_t.stream().map(logit -> exp(logit - maxLogit)).collect(Collectors.toList());
        double sumProbs = probs.stream().mapToDouble(Double::doubleValue).sum();
        return probs.stream().map(p -> p / sumProbs).collect(Collectors.toList());
    }
}
