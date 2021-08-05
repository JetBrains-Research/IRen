package org.jetbrains.id.names.suggesting.naturalize;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager;
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor;
import org.jetbrains.id.names.suggesting.impl.IdNamesNGramModelRunner;

public class GlobalNaturalizeContributor extends NaturalizeContributor {
    @Override
    public IdNamesNGramModelRunner getModelRunnerToContribute(@NotNull PsiVariable variable) {
        return (IdNamesNGramModelRunner) IdNamesSuggestingModelManager.getInstance()
                .getModelRunner(GlobalVariableNamesContributor.class);
    }
}
