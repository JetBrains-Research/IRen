package org.jetbrains.iren.contributors;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiVariable;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelManager;
import org.jetbrains.iren.api.VariableNamesContributor;
import org.jetbrains.iren.impl.NGramModelRunner;
import org.jetbrains.iren.storages.VarNamePrediction;
import org.jetbrains.kotlin.psi.KtProperty;

import java.util.ArrayList;
import java.util.List;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    public static final List<Class<? extends PsiNameIdentifierOwner>> SUPPORTED_TYPES = new ArrayList<>();

    static {
        SUPPORTED_TYPES.add(PsiVariable.class);
        SUPPORTED_TYPES.add(KtProperty.class);
    }

    @Override
    public int contribute(@NotNull PsiNameIdentifierOwner variable, @NotNull List<VarNamePrediction> predictionList) {
        NGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return 0;
        }
        PsiFile file = variable.getContainingFile();
        if (this.forgetFile()) {
            ModelManager.getInstance().invokeLater(file.getProject(), (String name) -> modelRunner.learnPsiFile(file));
            modelRunner.forgetPsiFile(file);
        }

        predictionList.addAll(modelRunner.suggestNames(variable, forgetContext()));

        return modelRunner.getModelPriority();
    }

    @Override
    public @NotNull Pair<Double, Integer> getProbability(@NotNull PsiNameIdentifierOwner variable) {
        NGramModelRunner modelRunner = getModelRunnerToContribute(variable);
        if (modelRunner == null || !isSupported(variable)) {
            return new Pair<>(0.0, 0);
        }

        PsiFile file = variable.getContainingFile();
        if (this.forgetFile()) {
            modelRunner.forgetPsiFile(file);
        }

        @NotNull Pair<Double, Integer> prob = modelRunner.getProbability(variable, forgetContext());

        if (this.forgetFile()) {
            modelRunner.learnPsiFile(file);
        }
        return prob;
    }

    protected abstract boolean forgetFile();

    protected abstract boolean forgetContext();

    public abstract @Nullable NGramModelRunner getModelRunnerToContribute(@NotNull PsiNameIdentifierOwner variable);

    private static boolean isSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        return SUPPORTED_TYPES.stream().anyMatch(type -> type.isInstance(identifierOwner));
    }
}
