package org.jetbrains.id.names.suggesting.api;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiVariable;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.VarNamePrediction;

import java.util.List;

public interface VariableNamesContributor {
    ExtensionPointName<VariableNamesContributor> EP_NAME =
            ExtensionPointName.create("org.jetbrains.id.names.suggesting.variableNamesContributor");

    /**
     * Contribute some variable names.
     *
     * @param variable          variable which name we want to predict.
     * @param predictionList    container which contains all predictions.
     * @return                  priority of contribution
     */
    int contribute(@NotNull PsiVariable variable, @NotNull List<VarNamePrediction> predictionList, boolean forgetUsages);

    /**
     * Get conditional probability of variable name.
     *
     * @param variable some variable.
     * @return pair of probability and model priority.
     */
    @NotNull Pair<Double, Integer> getProbability(@NotNull PsiVariable variable, boolean forgetUsages);
}
