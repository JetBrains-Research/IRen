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
import org.jetbrains.id.names.suggesting.VocabularyManager;
import org.jetbrains.id.names.suggesting.storages.IntContext;
import org.jetbrains.id.names.suggesting.storages.VarNamePrediction;
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

import static java.lang.Math.*;
import static org.jetbrains.id.names.suggesting.IdNamesSuggestingService.PREDICTION_CUTOFF;

public class NGramModelRunner {
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

    public NGramModelRunner(List<Class<? extends PsiNameIdentifierOwner>> supportedTypes, boolean isLargeCorpora) {
        myModel = new JMModel(6, 0.5, isLargeCorpora ? new GigaCounter() : new ArrayTrieCounter());
        this.setSupportedTypes(supportedTypes);
    }

    public NGramModelRunner(NGramModel model,
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

    public @NotNull List<VarNamePrediction> suggestNames(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass, @NotNull IntContext intContext) {
        IntContext unknownContext = intContext.with(0);
        Set<Integer> candidates = new HashSet<>();
        for (int idx : intContext.getVarIdxs()) {
            candidates.addAll(getCandidates(unknownContext.getTokens(), idx, getIdTypeFilter(identifierClass)));
        }
        return rankCandidates(candidates, unknownContext);
    }

    private @NotNull List<VarNamePrediction> rankCandidates(@NotNull Set<Integer> candidates, @NotNull IntContext intContext) {
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
        return predictions.subList(0, min(predictions.size(), PREDICTION_CUTOFF));
    }

    private static @NotNull List<Double> softmax(@NotNull List<Double> logits, double temperature) {
        if (logits.isEmpty()) return logits;
        List<Double> logits_t = logits.stream().map(l -> l / temperature).collect(Collectors.toList());
        Double maxLogit = Collections.max(logits_t);
        List<Double> probs = logits_t.stream().map(logit -> exp(logit - maxLogit)).collect(Collectors.toList());
        double sumProbs = probs.stream().mapToDouble(Double::doubleValue).sum();
        return probs.stream().map(p -> p / sumProbs).collect(Collectors.toList());
    }

    private double getLogProb(IntContext intContext) {
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

    public @NotNull Pair<Double, Integer> getProbability(IntContext intContext) {
        return new Pair<>(getLogProb(intContext), getModelPriority());
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

    public void forgetContext(@NotNull IntContext context) {
        myModel.forget(context.getTokens());
    }

    public void learnContext(@NotNull IntContext context) {
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

    public void learnPsiFile(@NotNull PsiFile file) {
        myModel.learn(myVocabulary.toIndices(lexPsiFile(file)));
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

    public int getOrder() {
        return myModel.getOrder();
    }

    private static final Path MODEL_DIRECTORY = Paths.get(PathManager.getSystemPath(), "model");

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
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
