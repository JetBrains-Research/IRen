package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelManager;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.impl.NGramModelRunner;

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
    public @Nullable NGramModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable) {
        String name = ModelManager.getName(variable.getProject(), variable.getLanguage());
        if (ModelStatsService.getInstance().isUsable(name)) {
            return ModelManager.getInstance().getModelRunner(name);
        }
        return null;
    }
}
