package org.jetbrains.iren.services;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.Collection;
import java.util.List;

public interface IRenSuggestingService {
    int PREDICTION_CUTOFF = 10;

    static IRenSuggestingService getInstance(@NotNull Project project) {
        return project.getService(IRenSuggestingService.class);
    }

    @NotNull List<VarNamePrediction> suggestVariableName(Project project, @NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement selectedElement, Collection<ModelType> modelTypes);

    default @NotNull List<VarNamePrediction> suggestVariableName(Project project, @NotNull PsiNameIdentifierOwner variable, Collection<ModelType> modelTypes) {
        return suggestVariableName(project, variable, null, modelTypes);
    }

    @NotNull Double getVariableNameProbability(@NotNull PsiNameIdentifierOwner variable);

    @NotNull Context.Statistics getVariableContextStatistics(@NotNull PsiNameIdentifierOwner variable);
}

