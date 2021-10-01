package org.jetbrains.iren.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenSuggestingService;
import org.jetbrains.iren.ModelManager;
import org.jetbrains.iren.ModelStatsService;
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor;
import org.jetbrains.iren.utils.LanguageSupporter;

import java.util.*;

public class IRenMemberInplaceRenamer extends MemberInplaceRenamer {
    private final LinkedHashMap<String, Double> myNameProbs = new LinkedHashMap<>();

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @NotNull Editor editor) {
        this(elementToRename, null, editor);
    }

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public IRenMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename,
                                    @Nullable PsiElement substituted,
                                    @NotNull Editor editor,
                                    @Nullable String initialName,
                                    @Nullable String oldName) {
        super(elementToRename, substituted, editor, initialName, oldName);
    }

    @Override
    public boolean performInplaceRefactoring(@Nullable LinkedHashSet<String> nameSuggestions) {
        if (nameSuggestions == null) nameSuggestions = new LinkedHashSet<>();
        LanguageSupporter supporter = LanguageSupporter.getInstance(myElementToRename.getLanguage());
        if (ModelStatsService.getInstance().isUsable(
                ModelManager.getName(ProjectVariableNamesContributor.class, myProject, myElementToRename.getLanguage())) &&
                supporter != null && supporter.isVariable(myElementToRename)) {
            LinkedHashMap<String, Double> nameProbs = IRenSuggestingService.getInstance().suggestVariableName((PsiNameIdentifierOwner) myElementToRename);
            double unknownNameProb = nameProbs.getOrDefault(Vocabulary.unknownCharacter, 0.);
            double varNameProb = nameProbs.getOrDefault(myElementToRename.getText(), 0.) - 1e-4;
            double threshold = Math.max(0.001, Math.max(unknownNameProb, varNameProb));
            for (Map.Entry<String, Double> e : nameProbs.entrySet()) {
                if (e.getValue() > threshold) {
                    myNameProbs.put(e.getKey(), e.getValue());
                }
            }
            nameSuggestions.addAll(myNameProbs.keySet());
        }
        return super.performInplaceRefactoring(nameSuggestions);
    }

    @Override
    protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
        NameSuggestionProvider.suggestNames(myElementToRename, selectedElement, myNameSuggestions);
        return new NewLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, selectedElement, shouldSelectAll(), myAdvertisementText, myNameProbs);
    }

    static class NewLookupExpression extends MyLookupExpression {
        private final LinkedHashMap<String, Double> namesProbs;
        private final HashMap<String, Integer> namesIndex = new HashMap<>();

        public NewLookupExpression(String name, @Nullable LinkedHashSet<String> names, @Nullable PsiNamedElement elementToRename, @Nullable PsiElement nameSuggestionContext, boolean shouldSelectAll, String advertisement, @NotNull LinkedHashMap<String, Double> namesProbs) {
            super(name, names, elementToRename, nameSuggestionContext, shouldSelectAll, advertisement);
            this.namesProbs = namesProbs;
            int i = 0;
            for (String key : namesProbs.keySet()) {
                namesIndex.put(key, i++);
            }
        }

        @Override
        public LookupElement[] calculateLookupItems(ExpressionContext context) {
            LookupElement[] lookupElements = super.calculateLookupItems(context);
            List<LookupElement> newLookupElements = new ArrayList<>();
            for (LookupElement lookupElement : lookupElements) {
                @NotNull String name = lookupElement.getLookupString();
                newLookupElements.add(
                        namesProbs.containsKey(name) ?
                                new MyLookup.NGram(lookupElement, namesProbs, namesIndex) :
                                new MyLookup.Default(lookupElement));
            }
            return newLookupElements.toArray(lookupElements);
        }
    }
}
