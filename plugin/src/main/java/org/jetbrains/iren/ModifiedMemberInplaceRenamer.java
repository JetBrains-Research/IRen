package org.jetbrains.iren;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class ModifiedMemberInplaceRenamer extends MemberInplaceRenamer {
    private LinkedHashMap<String, Double> myNameSuggestions;

    public ModifiedMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public void performInplaceRefactoring(@NotNull LinkedHashMap<String, Double> nameSuggestions) {
        this.myNameSuggestions = nameSuggestions;
        super.performInplaceRefactoring(new LinkedHashSet<>(myNameSuggestions.keySet()));
    }

    @Override
    protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
        return new NewLookupExpression(getInitialName(), myNameSuggestions, myElementToRename, selectedElement, shouldSelectAll(), myAdvertisementText);
    }

    static class NewLookupExpression extends MyLookupExpression {
        private final LinkedHashMap<String, Double> namesProbs;

        public NewLookupExpression(String name, @NotNull LinkedHashMap<String, Double> namesProbs, @Nullable PsiNamedElement elementToRename, @Nullable PsiElement nameSuggestionContext, boolean shouldSelectAll, String advertisement) {
            super(name, new LinkedHashSet<>(namesProbs.keySet()), elementToRename, nameSuggestionContext, shouldSelectAll, advertisement);
            this.namesProbs = namesProbs;
        }

        @Override
        public LookupElement[] calculateLookupItems(ExpressionContext context) {
            LookupElement[] lookupElements = super.calculateLookupItems(context);
            List<LookupElement> newLookupElements = new ArrayList<>();
            for (LookupElement lookupElement : lookupElements) {
                newLookupElements.add(new LookupElementDecorator<LookupElement>(lookupElement) {
                    @Override
                    public void renderElement(LookupElementPresentation presentation) {
                        super.renderElement(presentation);
                        presentation.setTypeText(String.format("%.3f", namesProbs.get(lookupElement.getLookupString())));
                    }
                });
            }
            return newLookupElements.toArray(lookupElements);
        }
    }
}
