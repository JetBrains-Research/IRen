package org.jetbrains.iren.utils;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenVariableNameSuggestionProvider;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.inspections.variable.RenameVariableQuickFix;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

public class RenameUtils {
    /**
     * Register a problem if IRen considers that variable name is inconsistent.
     *
     * @param variable   to be checked
     * @param holder     registers the problem
     * @param isOnTheFly true if inspection was run in non-batch mode
     */
    public static void visitVariable(PsiNameIdentifierOwner variable, ProblemsHolder holder, boolean isOnTheFly) {
        @NotNull Project project = holder.getProject();
        @Nullable PsiElement identifier = variable.getNameIdentifier();
        if (identifier == null) return;
        if (ConsistencyChecker.getInstance(project).isInconsistent(variable, isOnTheFly)) {
            holder.registerProblem(identifier,
                    RenameBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    new RenameVariableQuickFix()
            );
        }
    }

    /**
     * Add suggestions predicted by IRen model to {@code nameSuggestions}. Convenient static method that is used in all renamers.
     *
     * @param nameSuggestions   where to store predictions
     * @param elementToRename   IRen model suggests names for this element
     * @param selectedElement   element under caret
     * @param nameProbabilities stores probabilities of names
     */
    public static void addIRenPredictionsIfPossible(@NotNull LinkedHashSet<String> nameSuggestions,
                                                    @NotNull PsiNamedElement elementToRename,
                                                    @Nullable PsiElement selectedElement,
                                                    @NotNull LinkedHashMap<String, Double> nameProbabilities,
                                                    @NotNull LinkedHashMap<String, ModelType> modelTypes) {
        SuggestedNameInfo info = NameSuggestionProvider.suggestNames(elementToRename, selectedElement, nameSuggestions);
        if (info instanceof IRenVariableNameSuggestionProvider.IRenSuggestedNameInfo) {
            sortAndFilterSuggestions(nameSuggestions, elementToRename, nameProbabilities, modelTypes,
                    ((IRenVariableNameSuggestionProvider.IRenSuggestedNameInfo) info).getPredictions());
        }
    }

    private static void sortAndFilterSuggestions(@NotNull LinkedHashSet<String> nameSuggestions,
                                                 @NotNull PsiNamedElement elementToRename,
                                                 @NotNull LinkedHashMap<String, Double> nameProbabilities,
                                                 @NotNull LinkedHashMap<String, ModelType> modelTypes,
                                                 @NotNull List<VarNamePrediction> predictions) {
        double varNameProb = findVarNameProb(elementToRename, predictions);
        double threshold = Math.max(0.02, varNameProb);
        for (VarNamePrediction prediction : predictions) {
            if (prediction.getProbability() > threshold) {
                nameProbabilities.put(prediction.getName(), prediction.getProbability());
                modelTypes.put(prediction.getName(), prediction.getModelType());
            }
        }
        for (String name : nameSuggestions) {
            modelTypes.putIfAbsent(name, ModelType.DEFAULT);
        }
        nameSuggestions.clear();
        nameSuggestions.addAll(modelTypes.keySet());
    }

    private static double findVarNameProb(@NotNull PsiNamedElement elementToRename, @NotNull List<VarNamePrediction> predictions) {
        double varNameProb = 0;
        String name = elementToRename.getText();
        for (VarNamePrediction prediction : predictions) {
            if (prediction.getModelType() != ModelType.DEFAULT && Objects.equals(prediction.getName(), name))
                varNameProb = prediction.getProbability();
        }
        return varNameProb - 1e-4;
    }
}
