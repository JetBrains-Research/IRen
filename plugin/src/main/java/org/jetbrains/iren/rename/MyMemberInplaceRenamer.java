package org.jetbrains.iren.rename;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ModelManager;

import java.util.*;

public class MyMemberInplaceRenamer extends MemberInplaceRenamer {
    private final LinkedHashMap<String, Double> myNameProbs = new LinkedHashMap<>();

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @Nullable PsiElement substituted, @NotNull Editor editor) {
        super(elementToRename, substituted, editor);
    }

    public MyMemberInplaceRenamer(@NotNull PsiNamedElement elementToRename, @NotNull Editor editor) {
        this(elementToRename, null, editor);
    }

    public boolean performInplaceRefactoring(@NotNull LinkedHashMap<String, Double> nameProbs) {
        double unknownNameProb = nameProbs.getOrDefault(Vocabulary.unknownCharacter, 0.);
        double varNameProb = nameProbs.getOrDefault(myElementToRename.getText(), 0.) - 1e-4;
        double threshold = Math.max(0.001, Math.max(unknownNameProb, varNameProb));
        for (Map.Entry<String, Double> e : nameProbs.entrySet()) {
            if (e.getValue() > threshold) {
                myNameProbs.put(e.getKey(), e.getValue());
            }
        }
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
            for (LookupElement lookupElement : lookupElements) {
                @NotNull String name = lookupElement.getLookupString();
                newLookupElements.add(
                        namesProbs.containsKey(name) ?
                                new NGramLookupElement(lookupElement, namesProbs, namesIndex) :
                                new DefaultLookupElement(lookupElement));
            }
            return newLookupElements.toArray(lookupElements);
        }
    }

    public static class NGramLookupElement extends LookupElementDecorator<LookupElement> {
        public final double probability;
        public final int rank;

        protected NGramLookupElement(@NotNull LookupElement delegate, Map<String, Double> namesProbs, Map<String, Integer> namesIndex) {
            super(delegate);
            probability = namesProbs.get(getLookupString());
            rank = namesIndex.get(getLookupString());
        }

        @Override
        public void renderElement(LookupElementPresentation presentation) {
            super.renderElement(presentation);
            presentation.setTypeText(String.format("%.3f", probability));
        }

        @Override
        public void handleInsert(@NotNull InsertionContext context) {
            super.handleInsert(context);
            ModelManager.getInstance().invoke(context.getProject(), getLookupString());
        }
    }

    public static class DefaultLookupElement extends LookupElementDecorator<LookupElement> {
        protected DefaultLookupElement(@NotNull LookupElement delegate) {
            super(delegate);
        }

        @Override
        public void handleInsert(@NotNull InsertionContext context) {
            super.handleInsert(context);
            ModelManager.getInstance().invoke(context.getProject(), getLookupString());
        }
    }
}
