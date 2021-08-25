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
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ObjectUtils;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IdNamesSuggestingBundle;
import org.jetbrains.iren.VocabularyManager;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.StringCounter;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.utils.NotificationsUtil;
import org.jetbrains.iren.utils.PsiUtils;
import org.jetbrains.iren.IdNamesSuggestingService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.lang.Math.*;

public class NGramModelRunner {
    /**
     * {@link HashMap} from {@link Class} of identifier to {@link HashSet} of remembered identifiers of this {@link Class}.
     */
    private final HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> myRememberedIdentifiers = new HashMap<>();

    private final NGramModel myModel;
    private final Vocabulary myVocabulary = new Vocabulary();
    private boolean rememberPossibleNames = false;

    public HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> getRememberedIdentifiers() {
        return myRememberedIdentifiers;
    }

    public NGramModel getModel() {
        return myModel;
    }

    public Vocabulary getVocabulary() {
        return myVocabulary;
    }

    public NGramModelRunner(List<Class<? extends PsiNameIdentifierOwner>> supportedTypes, boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
        this.setSupportedTypes(supportedTypes);
    }

    public void setSupportedTypes(@NotNull List<Class<? extends PsiNameIdentifierOwner>> supportedTypes) {
        for (Class<? extends PsiNameIdentifierOwner> supportedType : supportedTypes) {
            myRememberedIdentifiers.putIfAbsent(supportedType, new HashSet<>());
        }
    }

    public int getModelPriority() {
        return myVocabulary.size();
    }

    public @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiVariable variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = Context.fromStringToInt(PsiUtils.getContext(variable, false), myVocabulary);
        if (forgetContext) {
            forgetContext(intContext);
        }

        Context<Integer> unknownContext = intContext.with(0);
        Set<Integer> candidates = new HashSet<>();
        for (int idx : intContext.getVarIdxs()) {
            candidates.addAll(getCandidates(unknownContext.getTokens(), idx, getIdTypeFilter(variable.getClass())));
        }
        @NotNull List<VarNamePrediction> result = rankCandidates(candidates, unknownContext);

        if (forgetContext) {
            learnContext(intContext);
        }
        return result;
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
        return predictions.subList(0, Math.min(predictions.size(), IdNamesSuggestingService.PREDICTION_CUTOFF));
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

    public @NotNull Pair<Double, Integer> getProbability(PsiVariable variable, boolean forgetContext) {
        @NotNull Context<Integer> intContext = Context.fromStringToInt(PsiUtils.getContext(variable, false), myVocabulary);
        if (forgetContext) {
            forgetContext(intContext);
        }

        Pair<Double, Integer> result = new Pair<>(getLogProb(intContext), getModelPriority());

        if (forgetContext) {
            learnContext(intContext);
        }
        return result;
    }

    private @NotNull Set<Integer> getCandidates(@NotNull List<Integer> tokenIdxs, int idx,
                                                @NotNull Predicate<Map.Entry<Integer, ?>> idTypeFilter) {
        return myModel.predictToken(tokenIdxs, idx)
                .entrySet()
                .stream()
                .filter(idTypeFilter)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public void forgetContext(@NotNull Context<Integer> context) {
        myModel.forget(context.getTokens());
    }

    public void learnContext(@NotNull Context<Integer> context) {
        myModel.learn(context.getTokens());
    }

    private @NotNull Predicate<Map.Entry<Integer, ?>> getIdTypeFilter(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass) {
        Class<? extends PsiNameIdentifierOwner> parentClass = getSupportedParentClass(identifierClass);
        return entry -> parentClass != null && myRememberedIdentifiers.get(parentClass).contains(entry.getKey());
    }

    private @Nullable Class<? extends PsiNameIdentifierOwner> getSupportedParentClass(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass) {
        for (Class<? extends PsiNameIdentifierOwner> c : myRememberedIdentifiers.keySet()) {
            if (c.isAssignableFrom(identifierClass)) {
                return c;
            }
        }
        return null;
    }

    public void learnProject(@NotNull Project project, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setIndeterminate(false);
        }
        Collection<VirtualFile> files = ReadAction.compute(() -> FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project)));
        final int[] progress = {0};
        final int total = files.size();
        Instant start = Instant.now();
        int vocabularyCutOff = AppSettingsState.getInstance().vocabularyCutOff;
        int maxTrainingTime = AppSettingsState.getInstance().maxTrainingTime;
        if (vocabularyCutOff > 0) {
            System.out.printf("Training vocabulary on %s...\n", project.getName());
            StringCounter counter = new StringCounter();
            files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                            (maxTrainingTime <= 0 || Duration.between(start, Instant.now()).minusSeconds(maxTrainingTime).isNegative()))
                    .forEach(file -> {
                        counter.putAll(ReadAction.compute(() -> lexPsiFile(Objects.requireNonNull(PsiManager.getInstance(project).findFile(file)))));
                        synchronized (progress) {
                            double fraction = ++progress[0] / (double) total;
                            if (total < 10 || progress[0] % (total / 10) == 0) {
                                System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
                            }
                            if (progressIndicator != null) {
                                progressIndicator.setFraction(fraction);
                            }
                        }
                    });
            VocabularyManager.clear(myVocabulary);
            counter.toVocabulary(myVocabulary, vocabularyCutOff);
            System.out.printf("Done in %s\n", Duration.between(start, Instant.now()));
        }
        System.out.printf("Training NGram model on %s...\n", project.getName());
        progress[0] = 0;
        rememberPossibleNames = true;
        Instant finalStart = Instant.now();
        files.parallelStream().filter(x -> (progressIndicator == null || !progressIndicator.isCanceled()) &&
                        (maxTrainingTime <= 0 || Duration.between(finalStart, Instant.now()).minusSeconds(maxTrainingTime).isNegative()))
                .forEach(file -> {
                    ReadAction.run(() -> ObjectUtils.consumeIfNotNull(PsiManager.getInstance(project).findFile(file), this::learnPsiFile));
                    synchronized (progress) {
                        double fraction = ++progress[0] / (double) total;
                        if (total < 10 || progress[0] % (total / 10) == 0) {
                            System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
                        }
                        if (progressIndicator != null) {
                            progressIndicator.setText2(file.getPath());
                            progressIndicator.setFraction(fraction);
                        }
                    }
                });
        Duration delta = Duration.between(start, Instant.now());
        NotificationsUtil.notify(project,
                "NGram model training is completed.",
                String.format("Time of training on %s: %d ms.",
                        project.getName(),
                        delta.toMillis()));
        System.out.printf("Done in %s\n", delta);
        System.out.printf("Vocabulary size: %d\n", myVocabulary.size());
        rememberPossibleNames = false;
    }

    public void learnPsiFile(@NotNull PsiFile file) {
        learnLexed(lexPsiFile(file));
    }

    private synchronized void learnLexed(List<String> lexed) {
        List<Integer> indices = myVocabulary.toIndices(lexed);
        if (myVocabulary.getWordIndices().size() != myVocabulary.getWords().size()) {
            throw new AssertionError("Something went wrong with vocabulary!");
        }
        myModel.learn(indices);
    }

    public void forgetPsiFile(@NotNull PsiFile file) {
        myModel.forget(myVocabulary.toIndices(lexPsiFile(file)));
    }

    private @NotNull List<String> lexPsiFile(@NotNull PsiFile file) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(PsiUtils::shouldLex)
                .toList()
                .stream()
                .peek(this::rememberIdName)
                .map(PsiUtils::processToken)
                .collect(Collectors.toList());
    }

    private void rememberIdName(PsiElement element) {
        if (rememberPossibleNames && element instanceof PsiIdentifier && element.getParent() instanceof PsiNameIdentifierOwner) {
            PsiNameIdentifierOwner parent = (PsiNameIdentifierOwner) element.getParent();
            Class<? extends PsiNameIdentifierOwner> parentClass = getSupportedParentClass(parent.getClass());
            if (parentClass != null) {
                synchronized (this) {
                    myRememberedIdentifiers.get(parentClass).add(myVocabulary.toIndex(element.getText()));
                }
            }
        }
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
                progressIndicator.setText2(IdNamesSuggestingBundle.message("saving.file", counterFile.getName()));
            }
            counterFile.getParentFile().mkdirs();
            counterFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(counterFile);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            myModel.getCounter().writeExternal(objectOutputStream);
            objectOutputStream.close();
            fileOutputStream.close();

            if (progressIndicator != null) {
                progressIndicator.setText2(IdNamesSuggestingBundle.message("saving.file", rememberedVariablesFile.getName()));
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
                progressIndicator.setText2(IdNamesSuggestingBundle.message("saving.file", vocabularyFile.getName()));
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
                    progressIndicator.setText2(IdNamesSuggestingBundle.message("loading.file", counterFile.getName()));
                }
                FileInputStream fileInputStream = new FileInputStream(counterFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                try {
                    myModel.getCounter().readExternal(objectInputStream);
                } finally {
                    objectInputStream.close();
                    fileInputStream.close();
                }

                if (progressIndicator != null) {
                    progressIndicator.setText2(IdNamesSuggestingBundle.message("loading.file", rememberedVariablesFile.getName()));
                }
                Gson gson = new Gson();
                JsonReader reader = new JsonReader(new FileReader(rememberedVariablesFile));
                mapToRemember(gson.fromJson(reader, HashMap.class));

                if (progressIndicator != null) {
                    progressIndicator.setText2(IdNamesSuggestingBundle.message("loading.file", vocabularyFile.getName()));
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

    private void mapToRemember(@NotNull HashMap<String, List<Double>> fromJson) {
        for (Double id : fromJson.get("interface com.intellij.psi.PsiVariable")) {
            myRememberedIdentifiers.get(PsiVariable.class).add(id.intValue());
        }
    }

    public void resolveCounter() {
        /**
         *  Invokes com.intellij.completion.ngram.slp.counting.giga.GigaCounter#resolve()
         */
        myModel.getCounter().getCount();
    }
}
