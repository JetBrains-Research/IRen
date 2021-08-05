package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

public class ProjectVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    public @Nullable IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        return (IdNamesNGramModelRunner) IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(this.getClass(), variable.getProject());
    }
}
