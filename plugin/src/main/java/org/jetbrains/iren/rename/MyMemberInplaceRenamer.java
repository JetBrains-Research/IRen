package org.jetbrains.iren.rename;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.InvokeLaterService;
import org.jetbrains.iren.stats.RenameVariableStatistics;

import java.util.*;

public class MyMemberInplaceRenamer extends MemberInplaceRenamer {
    private LinkedHashMap<String, Double> myNameProbs;

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement variable, @NotNull Editor editor) {
        this(variable, null, editor);
    }

    public boolean performInplaceRefactoring(@NotNull LinkedHashMap<String, Double> nameProbs) {
        this.myNameProbs = nameProbs;
        return super.performInplaceRefactoring(new LinkedHashSet<>(myNameProbs.keySet()));
    }

    @Override
    protected MyLookupExpression createLookupExpression(PsiElement selectedElement) {
        LinkedHashSet<String> names = new LinkedHashSet<>(myNameProbs.keySet());
        NameSuggestionProvider.suggestNames(myElementToRename, selectedElement, names);
        return new NewLookupExpression(getInitialName(), names, myElementToRename, selectedElement, shouldSelectAll(), myAdvertisementText, myNameProbs);
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
            boolean sendStatistics = UsageStatisticsPersistenceComponent.getInstance().isAllowed();
            RenameVariableStatistics stats = RenameVariableStatistics.getInstance();
            if (sendStatistics) {
                stats.total++;
            }
            for (LookupElement lookupElement : lookupElements) {
                @NotNull String name = lookupElement.getLookupString();
                newLookupElements.add(
                        namesProbs.containsKey(name) ?
                                new LookupElementDecorator<LookupElement>(lookupElement) {
                                    @Override
                                    public void renderElement(LookupElementPresentation presentation) {
                                        super.renderElement(presentation);
                                        presentation.setTypeText(String.format("%.3f", namesProbs.get(name)));
                                    }

                                    @Override
                                    public void handleInsert(@NotNull InsertionContext context) {
                                        super.handleInsert(context);
                                        InvokeLaterService.getInstance().acceptAll(getLookupString());
                                        if (sendStatistics) {
                                            stats.applied++;
                                            stats.ranks.add(namesIndex.get(name));
                                        }
                                    }
                                } :
                                new LookupElementDecorator<LookupElement>(lookupElement) {
                                    @Override
                                    public void handleInsert(@NotNull InsertionContext context) {
                                        super.handleInsert(context);
                                        InvokeLaterService.getInstance().acceptAll(getLookupString());
                                        if (sendStatistics) {
                                            stats.appliedDefault++;
                                        }
                                    }
                                });
            }
            return newLookupElements.toArray(lookupElements);
        }
    }
}
