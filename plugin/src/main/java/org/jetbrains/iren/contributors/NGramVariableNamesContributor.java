package org.jetbrains.iren.contributors;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiNameIdentifierOwner;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.ModelRunner;
import org.jetbrains.iren.VariableNamesContributor;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.services.NGramModelManager;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.List;

public abstract class NGramVariableNamesContributor implements VariableNamesContributor {
    @Override
    public @NotNull ModelType getModelType() {
        return ModelType.NGRAM;
    }

    @Override
    public synchronized double contribute(@NotNull PsiNameIdentifierOwner variable,
                                          @NotNull List<VarNamePrediction> predictionList) {
        Project project = ReadAction.compute(variable::getProject);
        ModelRunner modelRunner = getModelRunnerToContribute(project, variable);
        if (modelRunner == null || notSupported(variable)) {
            return 0;
        }
        if (forgetFile()) {
            NGramModelManager.getInstance(project)
                    .forgetFileIfNeeded(modelRunner, ReadAction.compute(variable::getContainingFile));
        }
        predictionList.addAll(modelRunner.suggestNames(variable));
        return modelRunner.getModelPriority();
    }

    @Override
    public synchronized @NotNull Pair<Double, Double> getProbability(@NotNull PsiNameIdentifierOwner variable) {
        Project project = ReadAction.compute(variable::getProject);
        ModelRunner modelRunner = getModelRunnerToContribute(project, variable);
        if (modelRunner == null || notSupported(variable)) {
            return new Pair<>(.0, .0);
        }
        if (forgetFile()) {
            NGramModelManager.getInstance(project)
                    .forgetFileIfNeeded(modelRunner, ReadAction.compute(variable::getContainingFile));
        }
        return modelRunner.getProbability(variable);
    }

    protected abstract boolean forgetFile();

    public abstract @Nullable ModelRunner getModelRunnerToContribute(Project project, @NotNull PsiNameIdentifierOwner variable);

    private static boolean notSupported(@NotNull PsiNameIdentifierOwner identifierOwner) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(identifierOwner.getLanguage());
        return supporter == null || !supporter.isVariableDeclaration(identifierOwner);
    }

    public @NotNull Context.Statistics getContextStatistics(@NotNull PsiNameIdentifierOwner variable) {
        Project project = ReadAction.compute(variable::getProject);
        ModelRunner modelRunner = getModelRunnerToContribute(project, variable);
        if (modelRunner == null || notSupported(variable)) {
            return Context.Statistics.EMPTY;
        }
        if (forgetFile()) {
            NGramModelManager.getInstance(project).forgetFileIfNeeded(modelRunner, ReadAction.compute(variable::getContainingFile));
        }
        return modelRunner.getContextStatistics(variable);
    }
}
