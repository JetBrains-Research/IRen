package org.jetbrains.iren.ngram;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.modeling.Model;
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.completion.ngram.slp.translating.VocabularyRunner;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import my.counting.trie.MapTrieCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.api.ModelRunner;
import org.jetbrains.iren.services.IRenSuggestingService;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class NGramModelRunner implements ModelRunner {
    protected final static String COUNTER_FILE = "counter.ser";
    protected final static String FORWARD_COUNTER_FILE = "forwardCounter.ser";
    protected final static String REVERSE_COUNTER_FILE = "reverseCounter.ser";
    protected final static String REMEMBER_IDENTIFIERS_FILE = "rememberedIdentifiers.json";
    protected final static String VOCABULARY_FILE = "vocabulary.txt";
    public static boolean DEFAULT_BIDIRECTIONAL = true;
    /**
     * {@link Set} of identifier names.
     */
    protected final Set<Integer> myRememberedIdentifiers;
    protected final Model myModel;
    protected final Vocabulary myVocabulary;
    protected final boolean biDirectional;
    protected final int order;
    protected boolean myTraining = false;
    protected LanguageSupporter mySupporter = null;

    public NGramModelRunner() {
        this(DEFAULT_BIDIRECTIONAL, 6);
    }

    public NGramModelRunner(boolean biDirectional, int order) {
        this(biDirectional ?
                        new BiDirectionalModel(new JMModel(order, 0.5, new MapTrieCounter()),
                                new JMModel(order, 0.5, new MapTrieCounter())) :
                        new JMModel(order, 0.5, new MapTrieCounter()),
                new Vocabulary(),
                new HashSet<>(),
                biDirectional,
                order);
    }

    public NGramModelRunner(Model model, Vocabulary vocabulary, Set<Integer> rememberedIdentifiers, boolean biDirectional, int order) {
        myModel = model;
        myVocabulary = vocabulary;
        myRememberedIdentifiers = rememberedIdentifiers;
        this.biDirectional = biDirectional;
        this.order = order;
    }

    public void train() {
        myTraining = true;
    }

    public void eval() {
        myTraining = false;
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

    @Override
    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable) {
        return suggestNames(variable, false);
    }

    @Override
    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = prepareContext(variable, forgetContext);
        Context<Integer> unknownContext = intContext.with(0);
        Set<Integer> candidates = new HashSet<>();
        for (int idx : intContext.getVarIdxs()) {
            candidates.addAll(getCandidates(unknownContext.getTokens(), idx));
        }
        return rankCandidates(candidates, unknownContext);
    }

    @NotNull
    private Context<Integer> prepareContext(@NotNull PsiNameIdentifierOwner variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = Context.fromStringToInt(getSupporter(variable).getContext(variable, false), myVocabulary);
        if (forgetContext) {
//            I don't try to relearn context after refactoring because forgetting
//            context makes sense only for models that trained on a single file.
//            It means that this model will be discarded and relearning things is a waste of the time.
            forgetContext(intContext);
        }
        return intContext;
    }

    private LanguageSupporter getSupporter(PsiElement element) {
        if (mySupporter == null) {
            mySupporter = LanguageSupporter.getInstance(element.getLanguage());
        }
        assert mySupporter.getLanguage() == element.getLanguage();
        return mySupporter;
    }

    public void forgetContext(@NotNull Context<Integer> context) {
        myModel.forget(context.getTokens());
    }

    private @NotNull Set<Integer> getCandidates(@NotNull List<Integer> tokenIdxs, int idx) {
        return myModel.predictToken(tokenIdxs, idx).keySet();
    }

    private @NotNull List<VarNamePrediction> rankCandidates(@NotNull Set<Integer> candidates,
                                                            @NotNull Context<Integer> intContext) {
        List<Integer> cs = new ArrayList<>();
        List<Double> logits = new ArrayList<>();
        candidates.stream()
                .filter(myRememberedIdentifiers::contains)
                .forEach(candidate -> {
                    cs.add(candidate);
                    logits.add(getLogProb(intContext.with(candidate)));
                });
//        List<Double> probs = logits;
        List<Double> probs = softmax(logits, 6);
        List<VarNamePrediction> predictions = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            String name = myVocabulary.toWord(cs.get(i));
            if (mySupporter.isStopName(name)) continue;
            predictions.add(new VarNamePrediction(name,
                    probs.get(i),
                    getModelPriority()));
        }
        predictions.sort((a, b) -> -Double.compare(a.getProbability(), b.getProbability()));
        return predictions.subList(0, Math.min(predictions.size(), IRenSuggestingService.PREDICTION_CUTOFF));
    }

    private double getLogProb(@NotNull Context<Integer> intContext) {
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

    @Override
    public int getOrder() {
        return order;
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

    @Override
    public int getModelPriority() {
        return myVocabulary.size();
    }

    @Override
    public @NotNull Pair<Double, Integer> getProbability(PsiNameIdentifierOwner variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = prepareContext(variable, forgetContext);
        return new Pair<>(getLogProb(intContext), getModelPriority());
    }

    public void learnContext(@NotNull Context<Integer> context) {
        myModel.learn(context.getTokens());
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

    private synchronized void learnLexed(List<String> lexed) {
        List<Integer> indices = myVocabulary.toIndices(lexed);
        if (myVocabulary.getWordIndices().size() != myVocabulary.getWords().size()) {
            throw new AssertionError("Something went wrong with vocabulary!");
        }
        myModel.learn(indices);
    }

    @Override
    public void forgetPsiFile(@NotNull PsiFile file) {
        myModel.forget(myVocabulary.toIndices(getSupporter(file).lexPsiFile(file)));
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
        File vocabularyFile = modelPath.resolve(VOCABULARY_FILE).toFile();
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return -1;
            progressIndicator.setText2(IRenBundle.message("saving.file", vocabularyFile.getName()));
        }
        try {
            vocabularyFile.createNewFile();
            VocabularyRunner.INSTANCE.write(myVocabulary, vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
        return vocabularyFile.length();
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
        File vocabularyFile = modelPath.resolve(VOCABULARY_FILE).toFile();
        return rememberedVariablesFile.exists() &&
                vocabularyFile.exists() &&
                loadCounters(modelPath, progressIndicator) &&
                loadIndicesToRemember(progressIndicator, rememberedVariablesFile) &&
                loadVocabulary(progressIndicator, vocabularyFile);
    }

    protected boolean loadCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
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

    private boolean loadVocabulary(@Nullable ProgressIndicator progressIndicator, File vocabularyFile) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return false;
            progressIndicator.setText2(IRenBundle.message("loading.file", vocabularyFile.getName()));
        }
        VocabularyManager.clear(myVocabulary);
        VocabularyManager.read(vocabularyFile, myVocabulary);
        return true;
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
}
