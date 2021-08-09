package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.impl.NGramModelRunner;

public class GlobalVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    protected boolean forgetFile() {
        return false;
    }

    @Override
    protected boolean forgetContext() {
        return false;
    }

    @Override
    public NGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        return IdNamesSuggestingModelManager.getInstance()
            .getModelRunner(this.getClass());
    }
}
