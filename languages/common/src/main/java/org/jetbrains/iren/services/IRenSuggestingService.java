package org.jetbrains.iren.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;

public interface IRenSuggestingService {
    int PREDICTION_CUTOFF = 10;

    static IRenSuggestingService getInstance() {
        return ApplicationManager.getApplication().getService(IRenSuggestingService.class);
    }

    @NotNull LinkedHashMap<String, Double> suggestVariableName(@NotNull PsiNameIdentifierOwner variable);

    @NotNull Double getVariableNameProbability(@NotNull PsiNameIdentifierOwner variable);
}
