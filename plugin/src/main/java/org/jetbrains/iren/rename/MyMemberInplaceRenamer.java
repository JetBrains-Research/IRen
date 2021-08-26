package org.jetbrains.iren.rename;

import com.intellij.codeInsight.completion.InsertionContext;
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
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.stats.RenameVariableStatistics;

import java.util.*;

public class MyMemberInplaceRenamer extends MemberInplaceRenamer {
    private LinkedHashMap<String, Double> myNameSuggestions;

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement variable, @NotNull Editor editor) {
        this(variable, null, editor);
    }

    public boolean performInplaceRefactoring(@NotNull LinkedHashMap<String, Double> nameSuggestions) {
        this.myNameSuggestions = nameSuggestions;
        return super.performInplaceRefactoring(new LinkedHashSet<>(myNameSuggestions.keySet()));
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
            List<LookupElement> lookupElementsList = Arrays.asList(lookupElements);
            List<LookupElement> newLookupElements = new ArrayList<>();
            boolean sendStatistics = AppSettingsState.getInstance().sendStatistics;
            RenameVariableStatistics stats = RenameVariableStatistics.getInstance();
            if (sendStatistics) {
                stats.total++;
            }
            ListIterator<LookupElement> it = lookupElementsList.listIterator();
            while (it.hasNext()) {
                int i = it.nextIndex();
                LookupElement lookupElement = it.next();
                newLookupElements.add(new LookupElementDecorator<LookupElement>(lookupElement) {
                    @Override
                    public void renderElement(LookupElementPresentation presentation) {
                        super.renderElement(presentation);
                        presentation.setTypeText(String.format("%.3f", namesProbs.get(lookupElement.getLookupString())));
                    }

                    @Override
                    public void handleInsert(@NotNull InsertionContext context) {
                        super.handleInsert(context);
                        if (sendStatistics) {
                            stats.applied++;
                            stats.ranks.add(i);
                        }
                    }
                });
            }
            return newLookupElements.toArray(lookupElements);
        }
    }
}
