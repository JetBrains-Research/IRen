package org.jetbrains.id.names.suggesting.contributors;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

public class FileVariableNamesContributor extends NGramVariableNamesContributor {
    @Override
    public IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        IdNamesNGramModelRunner modelRunner = new IdNamesNGramModelRunner(SUPPORTED_TYPES, false);
        modelRunner.learnPsiFile(variable.getContainingFile());
        return modelRunner;
    }
}
