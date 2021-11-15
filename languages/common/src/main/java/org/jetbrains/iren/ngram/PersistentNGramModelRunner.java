package org.jetbrains.iren.ngram;

import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.completion.ngram.slp.counting.trie.MapTrieCounter;
import com.intellij.completion.ngram.slp.counting.trie.PersistentCounterManager;
import com.intellij.completion.ngram.slp.counting.trie.persistent.PersistentCounter;
import com.intellij.completion.ngram.slp.modeling.Model;
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel;
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

public class PersistentNGramModelRunner extends NGramModelRunner {
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
            final Counter forwardCounter = removeCounter(((NGramModel) ((BiDirectionalModel) myModel).getForward()));
            long counterSize1 = saveCounter(forwardCounterFile,
                    forwardCounter,
                    progressIndicator);
            if (counterSize1 < 0) return counterSize1;
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            final Counter reverseCounter = removeCounter((NGramModel) ((BiDirectionalModel) myModel).getReverse());
            long counterSize2 = saveCounter(reverseCounterFile,
                    reverseCounter,
                    progressIndicator);
            if (counterSize2 < 0) return counterSize2;
            return counterSize1 + counterSize2;
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            final Counter counter = removeCounter((NGramModel) myModel);
            return saveCounter(counterFile,
                    counter,
                    progressIndicator);
        }
    }

    private @NotNull Counter removeCounter(@NotNull NGramModel model) {
        final Counter res = model.getCounter();
        model.setCounter(new MapTrieCounter());
        return res;
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
    protected boolean loadCounters(@NotNull Path modelPath, @Nullable ProgressIndicator progressIndicator) {
        if (biDirectional) {
            File forwardCounterFile = modelPath.resolve(FORWARD_COUNTER_FILE).toFile();
            File reverseCounterFile = modelPath.resolve(REVERSE_COUNTER_FILE).toFile();
            if (!forwardCounterFile.exists() || !reverseCounterFile.exists()) return false;

            PersistentCounter counter = loadCounter(forwardCounterFile, progressIndicator);
            if (counter == null) return false;
            ((NGramModel) ((BiDirectionalModel) myModel).getForward()).setCounter(counter);

            counter = loadCounter(reverseCounterFile, progressIndicator);
            if (counter == null) return false;
            ((NGramModel) ((BiDirectionalModel) myModel).getReverse()).setCounter(counter);
        } else {
            File counterFile = modelPath.resolve(COUNTER_FILE).toFile();
            if (!counterFile.exists()) return false;
            PersistentCounter counter = loadCounter(counterFile, progressIndicator);
            if (counter == null) return false;
            ((NGramModel) myModel).setCounter(counter);
        }
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
}
