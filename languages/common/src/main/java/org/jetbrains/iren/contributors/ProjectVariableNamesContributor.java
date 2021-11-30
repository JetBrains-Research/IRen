package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.ModelRunner;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.services.ModelsUsabilityService;

public class ProjectVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    protected boolean forgetFile() {
        return true;
    }

    @Override
    protected boolean forgetContext() {
        return false;
    }

    @Override
    public @Nullable ModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable) {
        String name = ModelManager.getName(variable.getProject(), variable.getLanguage());
        if (ModelsUsabilityService.getInstance().isUsable(name)) {
            return ModelManager.getInstance().getModelRunner(name);
        }
        return null;
    }
}
