package org.jetbrains.id.names.suggesting.impl;

import com.intellij.completion.ngram.slp.counting.giga.GigaCounter;
import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter;
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.completion.ngram.slp.translating.VocabularyRunner;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.PathManager;
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
import org.jetbrains.id.names.suggesting.IdNamesSuggestingBundle;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingService;
import org.jetbrains.id.names.suggesting.VarNamePrediction;
import org.jetbrains.id.names.suggesting.VocabularyManager;
import org.jetbrains.id.names.suggesting.api.IdNamesSuggestingModelRunner;
import org.jetbrains.id.names.suggesting.utils.NotificationsUtil;
import org.jetbrains.id.names.suggesting.utils.PsiUtils;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IdNamesNGramModelRunner implements IdNamesSuggestingModelRunner {
    /**
     * {@link HashMap} from {@link Class} of identifier to {@link HashSet} of remembered identifiers of this {@link Class}.
     */
    private HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> myRememberedIdentifiers = new HashMap<>();

    private final NGramModel myModel;
    private Vocabulary myVocabulary = new Vocabulary();

    public HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> getRememberedIdentifiers() {
        return myRememberedIdentifiers;
    }

    public NGramModel getModel() {
        return myModel;
    }

    public Vocabulary getVocabulary() {
        return myVocabulary;
    }

    public IdNamesNGramModelRunner(List<Class<? extends PsiNameIdentifierOwner>> supportedTypes, boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
        this.setSupportedTypes(supportedTypes);
    }

    public IdNamesNGramModelRunner(NGramModel model,
                                   Vocabulary vocabulary,
                                   HashMap<Class<? extends PsiNameIdentifierOwner>, HashSet<Integer>> rememberedIdentifiers) {
        myModel = model;
        myVocabulary = vocabulary;
        myRememberedIdentifiers = rememberedIdentifiers;
    }

    public void setSupportedTypes(List<Class<? extends PsiNameIdentifierOwner>> supportedTypes) {
        for (Class<? extends PsiNameIdentifierOwner> supportedType : supportedTypes) {
            myRememberedIdentifiers.putIfAbsent(supportedType, new HashSet<>());
        }
    }

    public int getModelPriority() {
        return myVocabulary.size();
    }

    /**
     * Makes predictions for the last token from a set of N-gram sequences.
     *
     * @param identifierClass class of identifier (to check if we support suggesting for it).
     * @param usageNGrams     n-grams from which model should get suggestions.
     * @return List of predictions.
     */
    @Override
    public @NotNull List<VarNamePrediction> suggestNames(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass, @NotNull List<List<String>> usageNGrams, boolean forgetUsages) {
        List<List<Integer>> allUsageNGramIndices = nGramToIndices(usageNGrams);
        if (forgetUsages) {
            allUsageNGramIndices.forEach(this::forgetUsage);
        }
        List<VarNamePrediction> predictionList = new ArrayList<>();
        int usagePrioritiesSum = 0;
        for (List<Integer> usageNGramIndices : allUsageNGramIndices) {
            usagePrioritiesSum += predictUsageName(predictionList, usageNGramIndices, getIdTypeFilter(identifierClass));
        }
        if (forgetUsages) {
            allUsageNGramIndices.forEach(this::learnUsage);
        }
        return rankUsagePredictions(predictionList, usagePrioritiesSum);
    }

    /**
     * Gets a conditional probability of the last token(in each n-gram sequence they are the same)
     * using the formula of total probability. So the final probability obtained as a weighted sum
     * of conditional probabilities(see {@link IdNamesNGramModelRunner#getUsageProbability}) for each n-gram sequence
     * with weights equal to the usagePriority (see {@link IdNamesNGramModelRunner#getUsagePriority}) of each sequence.
     *
     * @param usageNGrams n-grams from which model should get probability of the last token.
     * @return pair of probability and model priority.
     */
    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull List<List<String>> usageNGrams, boolean forgetUsages) {
        List<List<Integer>> allUsageNGramIndices = nGramToIndices(usageNGrams);
        if (forgetUsages) {
            allUsageNGramIndices.forEach(this::forgetUsage);
        }
        double probability = 0.0;
        int usagePrioritiesSum = 0;
        for (List<Integer> usageNGramIndices : allUsageNGramIndices) {
            Pair<Double, Integer> probPriority = getUsageProbability(usageNGramIndices);
            probability += probPriority.getFirst() * probPriority.getSecond();
            usagePrioritiesSum += probPriority.getSecond();
        }
        if (forgetUsages) {
            allUsageNGramIndices.forEach(this::learnUsage);
        }
        return new Pair<>(probability / usagePrioritiesSum, getModelPriority());
    }

    private @NotNull List<VarNamePrediction> rankUsagePredictions(@NotNull List<VarNamePrediction> predictionList, int usagePrioritiesSum) {
        Map<String, Double> rankedPredictions = new HashMap<>();
        for (VarNamePrediction prediction : predictionList) {
            Double prob = rankedPredictions.get(prediction.getName());
            double addition = prediction.getProbability() * prediction.getPriority() / usagePrioritiesSum;
            if (prob == null) { // If see this prediction for the first time just put it in the map
                rankedPredictions.put(prediction.getName(), addition);
            } else {
                rankedPredictions.put(prediction.getName(), prob + addition);
            }
        }
        return rankedPredictions.entrySet()
                .stream()
                .sorted((e1, e2) -> -Double.compare(e1.getValue(), e2.getValue()))
                .limit(IdNamesSuggestingService.PREDICTION_CUTOFF)
                .map(e -> new VarNamePrediction(e.getKey(), e.getValue(), getModelPriority()))
                .collect(Collectors.toList());
    }

    private @NotNull List<List<Integer>> nGramToIndices(@NotNull List<List<String>> usageNGrams) {
        return usageNGrams.stream().map(myVocabulary::toIndices).collect(Collectors.toList());
    }

    private int predictUsageName(@NotNull List<VarNamePrediction> predictionList,
                                 @NotNull List<Integer> usageNGramIndices,
                                 @NotNull Predicate<Map.Entry<Integer, ?>> idTypeFilter) {
        int usagePriority = getUsagePriority(usageNGramIndices);
        predictionList.addAll(myModel.predictToken(usageNGramIndices, usageNGramIndices.size() - 1)
                .entrySet()
                .stream()
                .filter(idTypeFilter)
                .map(e -> new VarNamePrediction(myVocabulary.toWord(e.getKey()),
                        toProb(e.getValue()),
                        usagePriority))
                .sorted((pred1, pred2) -> -Double.compare(pred1.getProbability(), pred2.getProbability()))
                // Anyway predictions will be filtered later.
                .collect(Collectors.toList()));
        return usagePriority;
    }

    /**
     * Gets probability of identifier usage.
     *
     * @param usageNGramIndices n-gram sequence.
     * @return pair of probability and usagePriority.
     */
    private @NotNull Pair<Double, Integer> getUsageProbability(List<Integer> usageNGramIndices) {
        int usagePriority = getUsagePriority(usageNGramIndices);
        double probability = toProb(myModel.modelAtIndex(usageNGramIndices, usageNGramIndices.size() - 1));
        return new Pair<>(probability, usagePriority);
    }

    /**
     * Gets estimation of context usage count according to {@link IdNamesNGramModelRunner#myModel}.
     * It is estimated as a weighted sum of context counts of different size.
     * <p>
     * Detailed explanation.
     * Consider context sequence {C_i, ..., C_k} as C(i,k) and order of n-gram as N.
     * Then final estimation of context usage count will be: sum from i=0 to N-1 of 1/2^i * count(C(i, N-1)).
     * It is very similar to Jelinek-Mercer smoothing, which is used in {@link com.intellij.completion.ngram.slp.modeling.ngram.JMModel}.
     * <p>
     * May be it is better to assign it to 1 for all sequences,
     * but imho we have to evaluate some metrics to make that decision.
     *
     * @param usageNGramIndices n-gram sequence, count of which we want to derive.
     * @return usage priority.
     */
    private int getUsagePriority(List<Integer> usageNGramIndices) {
        long priority = 0;
        long contextCount = 1;
        for (int index = usageNGramIndices.size() - 2; index >= 0; index--) {
            if (contextCount > 0) {
                long[] counts = myModel.getCounter().getCounts(usageNGramIndices.subList(index, usageNGramIndices.size()));
                contextCount = counts[1];
            }
            priority = priority / 2 + contextCount;
        }
        return Math.max(1, (int) priority);
    }

    private void forgetUsage(@NotNull List<Integer> usageNGramIndices) {
        myModel.forgetToken(usageNGramIndices, usageNGramIndices.size() - 1);
    }

    private void learnUsage(@NotNull List<Integer> usageNGramIndices) {
        myModel.learnToken(usageNGramIndices, usageNGramIndices.size() - 1);
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
        Collection<VirtualFile> files = FileTypeIndex.getFiles(JavaFileType.INSTANCE,
                GlobalSearchScope.projectScope(project));
        int progress = 0;
        final int total = files.size();
        System.out.printf("Training NGram model on %s...\n", project.getName());
        Instant start = Instant.now();
        for (VirtualFile file : files) {
            ObjectUtils.consumeIfNotNull(PsiManager.getInstance(project).findFile(file), this::learnPsiFile);
            double fraction = ++progress / (double) total;
            if (total < 10 || progress % (total / 10) == 0) {
                System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
            }
            if (progressIndicator != null) {
                progressIndicator.setText2(file.getPath());
                progressIndicator.setFraction(fraction);
            }
        }
        Instant end = Instant.now();
        Duration delta = Duration.between(start, end);
        NotificationsUtil.notify(project,
                "NGram model training is completed.",
                String.format("Time of training on %s: %d ms.",
                        project.getName(),
                        delta.toMillis()));
        System.out.printf("Done in %s\n", delta);
        System.out.printf("Vocabulary size: %d\n", myVocabulary.size());
    }

    @Override
    public void learnPsiFile(@NotNull PsiFile file) {
        myModel.learn(myVocabulary.toIndices(lexPsiFile(file)));
    }

    @Override
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
                .map(PsiElement::getText)
                .collect(Collectors.toList());
    }

    private void rememberIdName(PsiElement element) {
        if (element instanceof PsiIdentifier && element.getParent() instanceof PsiNameIdentifierOwner) {
            PsiNameIdentifierOwner parent = (PsiNameIdentifierOwner) element.getParent();
            Class<? extends PsiNameIdentifierOwner> parentClass = getSupportedParentClass(parent.getClass());
            if (parentClass != null) {
                myRememberedIdentifiers.get(parentClass).add(myVocabulary.toIndex(element.getText()));
            }
        }
    }

    private double toProb(@NotNull Pair<Double, Double> probConf) {
        double prob = probConf.getFirst();
        double conf = probConf.getSecond();
        return prob * conf + (1 - conf) / myVocabulary.size();
    }

    private static final Path MODEL_DIRECTORY = Paths.get(PathManager.getSystemPath(), "org/jetbrains/astrid/model");

    public double save(@Nullable ProgressIndicator progressIndicator) {
        return save(MODEL_DIRECTORY, progressIndicator);
    }

    public double save(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            progressIndicator.setText(IdNamesSuggestingBundle.message("saving.global.model"));
            progressIndicator.setText2("");
            progressIndicator.setIndeterminate(true);
        }
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedIdentifiers.ser").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.ser").toFile();
        try {
            counterFile.getParentFile().mkdir();
            counterFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(counterFile);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            myModel.getCounter().writeExternal(objectOutputStream);
            objectOutputStream.close();
            fileOutputStream.close();

            rememberedVariablesFile.createNewFile();
            fileOutputStream = new FileOutputStream(rememberedVariablesFile);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(myRememberedIdentifiers);
            objectOutputStream.close();
            fileOutputStream.close();

            vocabularyFile.createNewFile();
            VocabularyRunner.INSTANCE.write(myVocabulary, vocabularyFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return (counterFile.length() + vocabularyFile.length() + rememberedVariablesFile.length()) / (1024. * 1024);
    }

    public void load() {
        load(null);
    }

    public void load(@Nullable ProgressIndicator progressIndicator) {
        load(MODEL_DIRECTORY, progressIndicator);
    }

    public void load(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator) {
        File counterFile = model_directory.resolve("counter.ser").toFile();
        File rememberedVariablesFile = model_directory.resolve("rememberedIdentifiers.ser").toFile();
        File vocabularyFile = model_directory.resolve("vocabulary.ser").toFile();
        if (counterFile.exists() && rememberedVariablesFile.exists() && vocabularyFile.exists()) {
            try {
                if (progressIndicator != null) {
                    progressIndicator.setIndeterminate(true);
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", counterFile.getName()));
                }
                FileInputStream fileInputStream = new FileInputStream(counterFile);
                ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
                myModel.getCounter().readExternal(objectInputStream);
                objectInputStream.close();
                fileInputStream.close();

                if (progressIndicator != null) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", rememberedVariablesFile.getName()));
                }
                fileInputStream = new FileInputStream(rememberedVariablesFile);
                objectInputStream = new ObjectInputStream(fileInputStream);
                myRememberedIdentifiers = (HashMap) objectInputStream.readObject();
                objectInputStream.close();
                fileInputStream.close();

                if (progressIndicator != null) {
                    progressIndicator.setText(IdNamesSuggestingBundle.message("loading.file", vocabularyFile.getName()));
                }
                myVocabulary = VocabularyManager.read(vocabularyFile);
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public int getOrder() {
        return myModel.getOrder();
    }
}
