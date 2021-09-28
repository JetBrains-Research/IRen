package org.jetbrains.iren.impl;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter;
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
    private final Set<Integer> myRememberedIdentifiers = new HashSet<>();

    private final NGramModel myModel;
    private final Vocabulary myVocabulary = new Vocabulary();

    private boolean myTraining = false;

    public void train() {
        myTraining = true;
    }

    public void eval() {
        myTraining = false;
    }

    public Set<Integer> getRememberedIdentifiers() {
        return myRememberedIdentifiers;
    }

    public NGramModel getModel() {
        return myModel;
    }

    public Vocabulary getVocabulary() {
        return myVocabulary;
    }

    public NGramModelRunner(boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
    }

    public int getModelPriority() {
        return myVocabulary.size();
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
        for (int idx : intContext.getVarIdxs()) {
            leftIdx = max(idx, rightIdx);
            rightIdx = min(idx + getOrder(), intContext.getTokens().size());
            for (int i = leftIdx; i < rightIdx; i++) {
                logProb += log(toProb(myModel.modelAtIndex(intContext.getTokens(), i)));
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

        Pair<Double, Integer> result = new Pair<>(getLogProb(intContext), getModelPriority());
        return result;
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
        return myModel.getOrder();
    }

    public double save(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedIdentifiers.json").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.txt").toFile();
        try {
            if (progressIndicator != null) {
                progressIndicator.setIndeterminate(true);
                progressIndicator.setText2(IRenBundle.message("saving.file", counterFile.getName()));
            }
            counterFile.getParentFile().mkdirs();
            counterFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(counterFile);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            myModel.getCounter().writeExternal(objectOutputStream);
            objectOutputStream.close();
            fileOutputStream.close();

            if (progressIndicator != null) {
                progressIndicator.setText2(IRenBundle.message("saving.file", rememberedVariablesFile.getName()));
            }
            rememberedVariablesFile.createNewFile();
            Gson gson = new GsonBuilder().create();
            fileOutputStream = new FileOutputStream(rememberedVariablesFile);
            OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8);
            try {
                writer.write(gson.toJson(myRememberedIdentifiers));
            } finally {
                writer.close();
                fileOutputStream.close();
            }

            if (progressIndicator != null) {
                progressIndicator.setText2(IRenBundle.message("saving.file", vocabularyFile.getName()));
            }
            vocabularyFile.createNewFile();
            VocabularyRunner.INSTANCE.write(myVocabulary, vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (counterFile.length() + vocabularyFile.length() + rememberedVariablesFile.length()) / (1024. * 1024);
    }

    public boolean load(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedIdentifiers.json").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.txt").toFile();
        if (counterFile.exists() && rememberedVariablesFile.exists() && vocabularyFile.exists()) {
            try {
                if (progressIndicator != null) {
                    progressIndicator.setIndeterminate(true);
                    progressIndicator.setText2(IRenBundle.message("loading.file", counterFile.getName()));
                }
                try (FileInputStream fileInputStream = new FileInputStream(counterFile);
                     ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream)) {
                    myModel.getCounter().readExternal(objectInputStream);
                }

                if (progressIndicator != null) {
                    progressIndicator.setText2(IRenBundle.message("loading.file", rememberedVariablesFile.getName()));
                }
                Gson gson = new Gson();
                JsonReader reader = new JsonReader(new FileReader(rememberedVariablesFile));
                mapToRemember(gson.fromJson(reader, HashSet.class));

                if (progressIndicator != null) {
                    progressIndicator.setText2(IRenBundle.message("loading.file", vocabularyFile.getName()));
                }
                VocabularyManager.clear(myVocabulary);
                VocabularyManager.read(vocabularyFile, myVocabulary);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    private void mapToRemember(@NotNull Collection<Double> fromJson) {
        for (Double id : fromJson) {
            myRememberedIdentifiers.add(id.intValue());
        }
    }

    /**
     * Invokes resolve from {@link com.intellij.completion.ngram.slp.counting.giga.GigaCounter}
     */
    public void resolveCounter() {
        myModel.getCounter().getCount();
    }
}
