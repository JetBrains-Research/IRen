package org.jetbrains.iren.utils;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.spellchecker.quickfixes.DictionarySuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.LanguageSupporter;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.inspections.variable.RenameVariableQuickFix;
import org.jetbrains.iren.services.ConsistencyChecker;
import org.jetbrains.iren.services.IRenSuggestingService;
import org.jetbrains.iren.services.NGramModelsUsabilityService;
import org.jetbrains.iren.storages.VarNamePrediction;

import java.util.*;

public class RenameUtils {
    /**
     * Register a problem if IRen considers that variable name is inconsistent.
     *
     * @param variable to be checked
     * @param holder   registers the problem
     */
    public static void visitVariable(PsiNameIdentifierOwner variable, ProblemsHolder holder) {
        @NotNull Project project = holder.getProject();
        if (NGramModelsUsabilityService.getInstance(project).isUsable(new ModelUtils().getName(variable.getProject(), variable.getLanguage()))
                && ConsistencyChecker.getInstance(project).isInconsistent(variable)) {
            @Nullable PsiElement identifier = variable.getNameIdentifier();
            if (identifier == null) return;
            holder.registerProblem(identifier,
                    RenameBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    new RenameVariableQuickFix(SmartPointerManager.createPointer(variable))
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
     * @param inferenceStratagy which models to use to suggest names
     */
    public static void addIRenPredictionsIfPossible(@NotNull LinkedHashSet<String> nameSuggestions,
                                                    @NotNull PsiNamedElement elementToRename,
                                                    @Nullable PsiElement selectedElement,
                                                    @NotNull LinkedHashMap<String, Double> nameProbabilities,
                                                    @NotNull LinkedHashMap<String, ModelType> modelTypes,
                                                    Set<ModelType> inferenceStratagy) {
        LanguageSupporter supporter = LanguageSupporter.getInstance(elementToRename.getLanguage());
        if (supporter != null
                && supporter.isVariableDeclaration(elementToRename)
                && supporter.isInplaceRenameAvailable(elementToRename)) {
            List<VarNamePrediction> varNamePredictions = IRenSuggestingService.getInstance(elementToRename.getProject())
                    .suggestVariableName(elementToRename.getProject(), (PsiNameIdentifierOwner) elementToRename, selectedElement, inferenceStratagy);
            filterSuggestions(nameSuggestions, elementToRename, nameProbabilities, modelTypes, varNamePredictions);
        }
    }

    private static void filterSuggestions(@NotNull LinkedHashSet<String> nameSuggestions,
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
            } else if (prediction.getModelType() == ModelType.DEFAULT) {
                modelTypes.putIfAbsent(prediction.getName(), prediction.getModelType());
            }
        }
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

    /**
     * Checks if typo rename inactive.
     *
     * @return boolean
     */
    public static boolean notTypoRename() {
        final DictionarySuggestionProvider provider = findDictionarySuggestionProvider();
        return provider == null || provider.shouldCheckOthers();
    }

    @Nullable
    private static DictionarySuggestionProvider findDictionarySuggestionProvider() {
        for (Object extension : NameSuggestionProvider.EP_NAME.getExtensionList()) {
            if (extension instanceof DictionarySuggestionProvider) {
                return (DictionarySuggestionProvider) extension;
            }
        }
        return null;
    }
}
