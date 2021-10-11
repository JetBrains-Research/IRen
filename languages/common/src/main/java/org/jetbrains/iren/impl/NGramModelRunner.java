package org.jetbrains.iren.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.IRenSuggestingService;
import org.jetbrains.iren.ModelManager;
import org.jetbrains.iren.VocabularyManager;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.utils.LanguageSupporter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class NGramModelRunner {
    /**
     * {@link Set} of identifier names.
     */
    private final Set<Integer> myRememberedIdentifiers;

    private final Model myModel;
    private final Vocabulary myVocabulary;

    private boolean myTraining = false;
    private final boolean biDirectional;
    private final int order;

    public static boolean DEFAULT_BIDIRECTIONAL = true;

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

    public NGramModelRunner() {
        this(true, DEFAULT_BIDIRECTIONAL, 6);
    }

    public NGramModelRunner(boolean isLargeCorpora) {
        this(isLargeCorpora, false, 6);
    }

    public NGramModelRunner(boolean isLargeCorpora, boolean biDirectional, int order) {
        this.biDirectional = biDirectional;
        this.order = order;
        myVocabulary = new Vocabulary();
        myRememberedIdentifiers = new HashSet<>();
        if (biDirectional) {
            myModel = new BiDirectionalModel(new JMModel(order, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter()),
                    new JMModel(order, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter()));
        } else {
            myModel = new JMModel(order, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
        }
    }

    public NGramModelRunner(Model model, Vocabulary vocabulary, Set<Integer> rememberedIdentifiers, boolean biDirectional, int order){
        myModel = model;
        myVocabulary = vocabulary;
        myRememberedIdentifiers = rememberedIdentifiers;
        this.biDirectional = biDirectional;
        this.order = order;
    }

    public int getModelPriority() {
        return myVocabulary.size();
    }

    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable) {
        return suggestNames(variable, false);
    }

    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = Context.fromStringToInt(LanguageSupporter.getInstance(variable.getLanguage())
                .getContext(variable, false), myVocabulary);
        if (forgetContext) {
            ModelManager.getInstance().invokeLater(variable.getProject(),
                    (String name) -> learnContext(name != null ?
                            intContext.with(myVocabulary.toIndex(name)) :
                            intContext));
            forgetContext(intContext);
        }

        Context<Integer> unknownContext = intContext.with(0);
        Set<Integer> candidates = new HashSet<>();
        for (int idx : intContext.getVarIdxs()) {
            candidates.addAll(getCandidates(unknownContext.getTokens(), idx));
        }
        return rankCandidates(candidates, unknownContext);
    }

    private @NotNull List<VarNamePrediction> rankCandidates(@NotNull Set<Integer> candidates, @NotNull Context<Integer> intContext) {
        List<Integer> cs = new ArrayList<>();
        List<Double> logits = new ArrayList<>();
        candidates.forEach(candidate -> {
            cs.add(candidate);
            logits.add(getLogProb(intContext.with(candidate)));
        });
//        List<Double> probs = logits;
        List<Double> probs = softmax(logits, 6);
        List<VarNamePrediction> predictions = new ArrayList<>();
        for (int i = 0; i < cs.size(); i++) {
            predictions.add(new VarNamePrediction(myVocabulary.toWord(cs.get(i)),
                    probs.get(i),
                    getModelPriority()));
        }
        predictions.sort((a, b) -> -Double.compare(a.getProbability(), b.getProbability()));
        return predictions.subList(0, Math.min(predictions.size(), IRenSuggestingService.PREDICTION_CUTOFF));
    }

    private static @NotNull List<Double> softmax(@NotNull List<Double> logits, double temperature) {
        if (logits.isEmpty()) return logits;
        List<Double> logits_t = logits.stream().map(l -> l / temperature).collect(Collectors.toList());
        Double maxLogit = Collections.max(logits_t);
        List<Double> probs = logits_t.stream().map(logit -> exp(logit - maxLogit)).collect(Collectors.toList());
        double sumProbs = probs.stream().mapToDouble(Double::doubleValue).sum();
        return probs.stream().map(p -> p / sumProbs).collect(Collectors.toList());
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

    public @NotNull Pair<Double, Integer> getProbability(PsiNameIdentifierOwner variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = Context.fromStringToInt(LanguageSupporter.getInstance(variable.getLanguage())
                .getContext(variable, false), myVocabulary);
        if (forgetContext) {
            ModelManager.getInstance().invokeLater(variable.getProject(),
                    (String name) -> learnContext(name != null ?
                            intContext.with(myVocabulary.toIndex(name)) :
                            intContext));
            forgetContext(intContext);
        }

        return new Pair<>(getLogProb(intContext), getModelPriority());
    }

    private @NotNull Set<Integer> getCandidates(@NotNull List<Integer> tokenIdxs, int idx) {
        return myModel.predictToken(tokenIdxs, idx)
                .keySet()
                .stream()
                .filter(myRememberedIdentifiers::contains)
                .collect(Collectors.toSet());
    }

    public void forgetContext(@NotNull Context<Integer> context) {
        myModel.forget(context.getTokens());
    }

    public void learnContext(@NotNull Context<Integer> context) {
        myModel.learn(context.getTokens());
    }

    public void learnPsiFile(@NotNull PsiFile file) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(file.getLanguage());
        if (supporter == null) return;
        @NotNull List<String> lexed = supporter.lexPsiFile(file, myTraining ? rememberIdName(supporter) : null);
        learnLexed(lexed);
    }

    private synchronized void learnLexed(List<String> lexed) {
        List<Integer> indices = myVocabulary.toIndices(lexed);
        if (myVocabulary.getWordIndices().size() != myVocabulary.getWords().size()) {
            throw new AssertionError("Something went wrong with vocabulary!");
        }
        myModel.learn(indices);
    }

    public void forgetPsiFile(@NotNull PsiFile file) {
        myModel.forget(myVocabulary.toIndices(LanguageSupporter.getInstance(file.getLanguage()).lexPsiFile(file)));
    }

    private Consumer<PsiElement> rememberIdName(LanguageSupporter supporter) {
        return (PsiElement element) -> {
            if (supporter.isVariableDeclaration(element)) {
                synchronized (this) {
                    myRememberedIdentifiers.add(myVocabulary.toIndex(element.getText()));
                }
            }
        };
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }

    public int getOrder() {
        return order;
    }

    private final static String COUNTER_FILE = "counter.ser";
    private final static String FORWARD_COUNTER_FILE = "forwardCounter.ser";
    private final static String REVERSE_COUNTER_FILE = "reverseCounter.ser";
    private final static String REMEMBER_IDENTIFIERS_FILE = "rememberedIdentifiers.json";
    private final static String VOCABULARY_FILE = "vocabulary.txt";

    public double save(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        model_directory = model_directory.resolve(myModel.toString() + "_" + order);
        File rememberedVariablesFile = model_directory.resolve(REMEMBER_IDENTIFIERS_FILE).toFile();
        File vocabularyFile = model_directory.resolve(VOCABULARY_FILE).toFile();
        vocabularyFile.getParentFile().mkdirs();
        long counterSize = 0;
        if (biDirectional) {
            File forwardCounterFile = model_directory.resolve(FORWARD_COUNTER_FILE).toFile();
            counterSize += saveCounter(forwardCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter(),
                    progressIndicator);

            File reverseCounterFile = model_directory.resolve(REVERSE_COUNTER_FILE).toFile();
            counterSize += saveCounter(reverseCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter(),
                    progressIndicator);
        } else {
            File counterFile = model_directory.resolve(COUNTER_FILE).toFile();
            counterSize += saveCounter(counterFile,
                    ((NGramModel) myModel).getCounter(),
                    progressIndicator);
        }
        try {
            if (progressIndicator != null) {
                if (progressIndicator.isCanceled()) return 0;
                progressIndicator.setText2(IRenBundle.message("saving.file", rememberedVariablesFile.getName()));
            }
            rememberedVariablesFile.createNewFile();
            Gson gson = new GsonBuilder().create();
            try (FileOutputStream fileOutputStream = new FileOutputStream(rememberedVariablesFile);
                 OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
                writer.write(gson.toJson(myRememberedIdentifiers));
            }

            if (progressIndicator != null) {
                if (progressIndicator.isCanceled()) return 0;
                progressIndicator.setText2(IRenBundle.message("saving.file", vocabularyFile.getName()));
            }
            vocabularyFile.createNewFile();
            VocabularyRunner.INSTANCE.write(myVocabulary, vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (counterSize + vocabularyFile.length() + rememberedVariablesFile.length()) / (1024. * 1024);
    }

    private long saveCounter(@NotNull File file, @NotNull Counter counter, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return 0;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2(IRenBundle.message("saving.file", file.getName()));
        }
        long counterSize = 0;
        try {
            file.createNewFile();
            try (FileOutputStream fileOutputStream = new FileOutputStream(file);
                 ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
                counter.writeExternal(objectOutputStream);
                counterSize = file.length();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return counterSize;
    }

    public boolean load(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        model_directory = model_directory.resolve(myModel.toString() + "_" + order);
        File rememberedVariablesFile = model_directory.resolve(REMEMBER_IDENTIFIERS_FILE).toFile();
        File vocabularyFile = model_directory.resolve(VOCABULARY_FILE).toFile();
        boolean exists = rememberedVariablesFile.exists() && vocabularyFile.exists();
        if (biDirectional) {
            File forwardCounterFile = model_directory.resolve(FORWARD_COUNTER_FILE).toFile();
            File reverseCounterFile = model_directory.resolve(REVERSE_COUNTER_FILE).toFile();
            exists &= forwardCounterFile.exists() && reverseCounterFile.exists();
            if (!exists) return false;
            if (!loadCounter(forwardCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter(),
                    progressIndicator)) return false;
            if (!loadCounter(reverseCounterFile,
                    ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter(),
                    progressIndicator)) return false;
        } else {
            File counterFile = model_directory.resolve(COUNTER_FILE).toFile();
            exists &= counterFile.exists();
            if (!exists) return false;
            if (!loadCounter(counterFile,
                    ((NGramModel) myModel).getCounter(),
                    progressIndicator)) return false;
        }
        try {
            if (progressIndicator != null) {
                if (progressIndicator.isCanceled()) return false;
                progressIndicator.setText2(IRenBundle.message("loading.file", rememberedVariablesFile.getName()));
            }
            Gson gson = new Gson();
            JsonReader reader = new JsonReader(new FileReader(rememberedVariablesFile));
            indicesToRemember(gson.fromJson(reader, HashSet.class));

            if (progressIndicator != null) {
                if (progressIndicator.isCanceled()) return false;
                progressIndicator.setText2(IRenBundle.message("loading.file", vocabularyFile.getName()));
            }
            VocabularyManager.clear(myVocabulary);
            VocabularyManager.read(vocabularyFile, myVocabulary);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean loadCounter(File counterFile, Counter counter, ProgressIndicator progressIndicator) {
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

    private void indicesToRemember(@NotNull Collection<Double> fromJson) {
        for (Double id : fromJson) {
            myRememberedIdentifiers.add(id.intValue());
        }
    }

    /**
     * Invokes resolve from {@link com.intellij.completion.ngram.slp.counting.giga.GigaCounter}
     */
    public void resolveCounter() {
        if (biDirectional) {
            final BiDirectionalModel model = (BiDirectionalModel) this.myModel;
            ((NGramModel) model.getForward()).getCounter().getCount();
            ((NGramModel) model.getReverse()).getCounter().getCount();
        } else {
            ((NGramModel) myModel).getCounter().getCount();
        }
    }
}
