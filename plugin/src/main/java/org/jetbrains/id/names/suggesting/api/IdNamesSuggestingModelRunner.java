package org.jetbrains.id.names.suggesting.api;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.VarNamePrediction;

import java.util.List;

public interface IdNamesSuggestingModelRunner {
    /**
     * Makes predictions for the last token from a set of N-gram sequences.
     *
     * @param identifierClass class of identifier (to check if we support suggesting for it).
     * @param usageNGrams     n-grams from which model should get suggestions.
     * @return List of predictions.
     */
    @NotNull List<VarNamePrediction> suggestNames(@NotNull Class<? extends PsiNameIdentifierOwner> identifierClass, @NotNull List<List<String>> usageNGrams, boolean forgetUsages);

    /**
     * Predict probability of last token in a series of n-grams.
     *
     * @param usageNGrams  : n-grams from which model should get probability of the last token.
     * @param forgetUsages :
     * @return probability, modelPriority
     */
    @NotNull Pair<Double, Integer> getProbability(@NotNull List<List<String>> usageNGrams, boolean forgetUsages);

    void learnPsiFile(@NotNull PsiFile file);

    void forgetPsiFile(@NotNull PsiFile file);
}
