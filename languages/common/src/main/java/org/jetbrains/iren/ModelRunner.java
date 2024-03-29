package org.jetbrains.iren;

import com.intellij.completion.ngram.slp.modeling.Model;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.iren.storages.Vocabulary;

import java.nio.file.Path;
import java.util.List;

public interface ModelRunner {
    Model getModel();

    Vocabulary getVocabulary();

    void train();

    void eval();

    @NotNull List<VarNamePrediction> suggestNames(@NotNull PsiNameIdentifierOwner variable);

    @NotNull Context.Statistics getContextStatistics(@NotNull PsiNameIdentifierOwner variable);

    @NotNull Pair<Double, Double> getProbability(PsiNameIdentifierOwner variable);

    int getOrder();

    double getModelPriority();

    void learnPsiFile(@NotNull PsiFile file);

    void forgetPsiFile(@NotNull PsiFile file);

    double save(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator);

    boolean load(@NotNull Path model_directory, @Nullable ProgressIndicator progressIndicator);
}
