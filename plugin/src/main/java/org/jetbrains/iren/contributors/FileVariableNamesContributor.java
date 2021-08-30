package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.impl.NGramModelRunner;

public class FileVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    protected boolean forgetFile() {
        return false;
    }

    @Override
    protected boolean forgetContext() {
        return true;
    }

    @Override
    public NGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        NGramModelRunner modelRunner = new NGramModelRunner(SUPPORTED_TYPES, false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        return modelRunner;
    }
}