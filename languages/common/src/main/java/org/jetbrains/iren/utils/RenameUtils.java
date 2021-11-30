package org.jetbrains.iren.utils;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.spellchecker.quickfixes.DictionarySuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.inspections.variable.RenameVariableQuickFix;
import org.jetbrains.iren.services.*;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;

public class RenameUtils {
    /**
     * Register a problem if IRen considers that variable name is inconsistent.
     *
     * @param variable to be checked
     * @param holder   registers the problem
     */
    public static void visitVariable(PsiNameIdentifierOwner variable, ProblemsHolder holder) {
        if (!ModelsUsabilityService.getInstance().isUsable(ModelManager.getName(variable.getProject(), variable.getLanguage())))
            return;
        if (ConsistencyChecker.getInstance().isInconsistent(variable)) {
            @Nullable PsiElement identifier = variable.getNameIdentifier();
            if (identifier == null) return;
            holder.registerProblem(identifier,
                    IRenBundle.message("inspection.description.template"),
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
     * @param nameProbabilities stores probabilities of names
     */
    public static void addIRenPredictionsIfPossible(@NotNull LinkedHashSet<String> nameSuggestions,
                                                    @NotNull PsiNamedElement elementToRename,
                                                    @NotNull LinkedHashMap<String, Double> nameProbabilities) {
        LanguageSupporter supporter;
        try {
            supporter = LanguageSupporter.getInstance(elementToRename.getLanguage());
        } catch (NoSuchElementException ignore) {
            return;
        }
        if (ModelsUsabilityService.getInstance().isUsable(
                ModelManager.getName(elementToRename.getProject(), elementToRename.getLanguage())) && supporter.isVariable(elementToRename)) {
            LinkedHashMap<String, Double> nameProbs = IRenSuggestingService.getInstance().suggestVariableName((PsiNameIdentifierOwner) elementToRename);
            double unknownNameProb = nameProbs.getOrDefault(Vocabulary.unknownCharacter, 0.);
            double varNameProb = nameProbs.getOrDefault(elementToRename.getText(), 0.) - 1e-4;
            double threshold = Math.max(0.001, Math.max(unknownNameProb, varNameProb));
            for (Map.Entry<String, Double> e : nameProbs.entrySet()) {
                if (e.getValue() > threshold) {
                    nameProbabilities.put(e.getKey(), e.getValue());
                }
            }
            nameSuggestions.addAll(nameProbabilities.keySet());
        }
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
    public static DictionarySuggestionProvider findDictionarySuggestionProvider() {
        for (Object extension : NameSuggestionProvider.EP_NAME.getExtensionList()) {
            if (extension instanceof DictionarySuggestionProvider) {
                return (DictionarySuggestionProvider) extension;
            }
        }
        return null;
    }
}
