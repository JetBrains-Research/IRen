package org.jetbrains.iren.api;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.List;

public interface VariableNamesContributor {
    ExtensionPointName<VariableNamesContributor> EP_NAME =
            ExtensionPointName.create("org.jetbrains.iren.variableNamesContributor");

    /**
     * Contribute some variable names.
     *
     * @param variable          variable which name we want to predict.
     * @param predictionList    container which contains all predictions.
     * @return                  priority of contribution
     */
    int contribute(@NotNull PsiNameIdentifierOwner variable, @NotNull List<VarNamePrediction> predictionList);

    /**
     * Get conditional probability of variable name.
     *
     * @param variable some variable.
     * @return pair of probability and model priority.
     */
    @NotNull Pair<Double, Integer> getProbability(@NotNull PsiNameIdentifierOwner variable);
}
