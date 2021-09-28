package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiNameIdentifierOwner;
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
    public NGramModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable) {
        NGramModelRunner modelRunner = new NGramModelRunner(false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        return modelRunner;
    }
}
