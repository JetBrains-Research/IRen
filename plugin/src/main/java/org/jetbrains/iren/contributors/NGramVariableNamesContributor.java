package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.VariableNamesContributor;
import org.jetbrains.iren.services.ModelManager;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.List;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    @Override
    public synchronized int contribute(@NotNull PsiNameIdentifierOwner variable, @NotNull List<VarNamePrediction> predictionList) {
        ModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || notSupported(variable)) {
            return 0;
        }
        if (forgetFile()) {
            ModelManager.getInstance().forgetFileIfNeeded(modelRunner, variable.getContainingFile());
        }
        predictionList.addAll(modelRunner.suggestNames(variable, forgetContext()));
        return modelRunner.getModelPriority();
    }

    @Override
    public synchronized @NotNull Pair<Double, Integer> getProbability(@NotNull PsiNameIdentifierOwner variable) {
        ModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || notSupported(variable)) {
            return new Pair<>(0.0, 0);
        }
        if (forgetFile()) {
            ModelManager.getInstance().forgetFileIfNeeded(modelRunner, variable.getContainingFile());
        }
        return modelRunner.getProbability(variable, forgetContext());
    }

    protected abstract boolean forgetFile();

    protected abstract boolean forgetContext();

    public abstract @Nullable ModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable);

    private static boolean notSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return !LanguageSupporter.getInstance(identifierOwner.getLanguage()).isVariableDeclaration(identifierOwner);
    }
}
