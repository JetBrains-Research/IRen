package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

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
        @NotNull IdNamesSuggestingModelManager modelManager = IdNamesSuggestingModelManager.getInstance();
        if (modelManager.isLoaded(ProjectVariableNamesContributor.class, variable.getProject())) {
            return modelManager.getModelRunner(this.getClass());
        }
        return null;
    }
}
