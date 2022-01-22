package org.jetbrains.iren.training;

import com.intellij.history.core.Paths;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleFunction;

public class ProgressBar {
    private final ProgressIndicator progressIndicator;
    private final String projectPath;
    int progress = 0;
    int total;
    boolean wasVocabTrained = false;

    public ProgressBar(int total, @Nullable ProgressIndicator progressIndicator, @Nullable String projectPath) {
        this.total = total;
        this.progressIndicator = progressIndicator;
        this.projectPath = projectPath;
    }

    public void clear(int newTotal) {
        progress = 0;
        total = newTotal;
    }

    public synchronized void vocabularyTrainingStep(VirtualFile file) {
        wasVocabTrained = true;
        step(file, fraction -> fraction / 2);
    }

    public synchronized void step(VirtualFile file, DoubleFunction<Double> modifyFraction) {
        double fraction = ++progress / (double) total;
        if (total < 10 || progress % (total / 10) == 0) {
            System.out.printf("Status:\t%.0f%%\r", fraction * 100.);
        }
        if (progressIndicator != null) {
            progressIndicator.setText2(projectPath != null ? Paths.relativeIfUnder(file.getPath(), projectPath) : file.getPath());
            progressIndicator.setFraction(modifyFraction.apply(fraction));
        }
    }

    public synchronized void trainingStep(VirtualFile file) {
        step(file, fraction -> wasVocabTrained ? 0.5 + fraction / 2 : fraction);
    }
}
