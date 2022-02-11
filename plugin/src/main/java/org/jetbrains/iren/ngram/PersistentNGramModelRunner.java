package org.jetbrains.iren.ngram;

import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.counting.trie.MapTrieCounter;
import com.intellij.completion.ngram.slp.counting.trie.PersistentCounterManager;
import com.intellij.completion.ngram.slp.counting.trie.persistent.PersistentCounter;
import com.intellij.completion.ngram.slp.modeling.Model;
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.storages.PersistentVocabulary;
import org.jetbrains.iren.storages.Vocabulary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class PersistentNGramModelRunner extends NGramModelRunner {
    @Override
    public String getVocabularyFile() {
        return "vocabulary";
    }

    public PersistentNGramModelRunner(NGramModelRunner modelRunner) {
        this(modelRunner.myModel,
                modelRunner.myVocabulary,
                modelRunner.myRememberedIdentifiers,
                modelRunner.biDirectional,
                modelRunner.order);
    }

    public PersistentNGramModelRunner() {
        super();
    }

    public PersistentNGramModelRunner(boolean biDirectional, int order) {
        super(biDirectional, order);
    }

    public PersistentNGramModelRunner(Model model, Vocabulary vocabulary, Set<Integer> rememberedIdentifiers, boolean biDirectional, int order) {
        super(model, vocabulary, rememberedIdentifiers, biDirectional, order);
    }

    protected long saveCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        if (biDirectional) {
            File forwardCounterFile = modelPath.resolve(FORWARD_COUNTER_FILE).toFile();
            final Counter forwardCounter = ((NGramModel) ((BiDirectionalModel) myModel).getForward()).getCounter();
            long counterSize1 = saveCounter(forwardCounterFile,
                    forwardCounter,
                    progressIndicator);
            if (counterSize1 < 0) return counterSize1;
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            final Counter reverseCounter = ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).getCounter();
            long counterSize2 = saveCounter(reverseCounterFile,
                    reverseCounter,
                    progressIndicator);
            if (counterSize2 < 0) return counterSize2;
            return counterSize1 + counterSize2;
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            final Counter counter = ((NGramModel) myModel).getCounter();
            return saveCounter(counterFile,
                    counter,
                    progressIndicator);
        }
    }

    @Override
    protected long saveCounter(@NotNull File file, @NotNull Counter counter, @Nullable ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return -1;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2(IRenBundle.message("saving.file", file.getName()));
        }
        try {
            file.createNewFile();
            PersistentCounterManager.serialize(file.getAbsolutePath(), counter);
            return file.length();
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }

    @Override
    protected void saveVocabulary(@NotNull File file) {
        PersistentVocabulary.saveVocabulary(myVocabulary, file.toPath());
    }

    @Override
    public boolean loadCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        if (biDirectional) {
            File forwardCounterFile = modelPath.resolve(FORWARD_COUNTER_FILE).toFile();
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            return forwardCounterFile.exists() && reverseCounterFile.exists() &&
                    loadModelCounter((NGramModel) ((BiDirectionalModel) myModel).getForward(),
                            forwardCounterFile,
                            progressIndicator) &&
                    loadModelCounter((NGramModel) ((BiDirectionalModel) myModel).getReverse(),
                            reverseCounterFile,
                            progressIndicator);
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            return counterFile.exists() && loadModelCounter((NGramModel) this.myModel, counterFile, progressIndicator);
        }
    }

    private boolean loadModelCounter(NGramModel model, File counterFile, @Nullable ProgressIndicator progressIndicator) {
        model.setCounter(new MapTrieCounter());
        PersistentCounter counter = loadCounter(counterFile, progressIndicator);
        if (counter == null) return false;
        model.setCounter(counter);
        return true;
    }

    private @Nullable PersistentCounter loadCounter(File counterFile, ProgressIndicator progressIndicator) {
        if (progressIndicator != null) {
            if (progressIndicator.isCanceled()) return null;
            progressIndicator.setIndeterminate(true);
            progressIndicator.setText2(IRenBundle.message("loading.file", counterFile.getName()));
        }
        return PersistentCounterManager.deserialize(counterFile.getAbsolutePath());
    }

    @Override
    protected Vocabulary loadVocabulary(File file) {
        return new PersistentVocabulary(file.toPath());
    }

    /**
     * Its counters cannot learn anything.
     * Just clear counterToForget of {@link com.intellij.completion.ngram.slp.counting.trie.persistent.CounterWithForgetting}
     */
    @Override
    public void learnPsiFile(@NotNull PsiFile file) {
        learnLexed(List.of());
    }
}
