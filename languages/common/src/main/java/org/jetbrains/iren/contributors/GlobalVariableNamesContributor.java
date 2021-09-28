package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.ModelManager;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.impl.NGramModelRunner;

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
    public NGramModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable) {
        if (ModelStatsService.getInstance().isUsable(this.getClass())) {
            return ModelManager.getInstance().getModelRunner(this.getClass());
        }
        return null;
    }
}
