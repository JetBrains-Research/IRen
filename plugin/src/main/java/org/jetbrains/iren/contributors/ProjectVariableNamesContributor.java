package org.jetbrains.iren.contributors;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.ModelManager;
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
    public @Nullable NGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        Project project = variable.getProject();
        if (ModelStatsService.getInstance().isLoaded(this.getClass(), project)) {
            return ModelManager.getInstance().getModelRunner(this.getClass(), project);
        }
        return null;
    }
}