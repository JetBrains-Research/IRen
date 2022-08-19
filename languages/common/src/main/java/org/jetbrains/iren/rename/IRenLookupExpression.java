package org.jetbrains.iren.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.config.ModelType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class IRenLookupExpression extends MyLookupExpression {
    private final LinkedHashMap<String, Double> myNameProbabilities;
    private final LinkedHashMap<String, ModelType> myModelTypes;

    public IRenLookupExpression(String name,
                                @Nullable LinkedHashSet<String> names,
                                @NotNull PsiNamedElement elementToRename,
                                @Nullable PsiElement nameSuggestionContext,
                                boolean shouldSelectAll,
                                String advertisement,
                                @NotNull LinkedHashMap<String, Double> nameProbabilities,
                                LinkedHashMap<String, ModelType> modelTypes) {
        super(name, names, elementToRename, nameSuggestionContext, shouldSelectAll, advertisement);
        myNameProbabilities = nameProbabilities;
        myModelTypes = modelTypes;
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
        LookupElement[] lookupElements = super.calculateLookupItems(context);
        List<LookupElement> newLookupElements = new ArrayList<>();
        for (LookupElement lookupElement : lookupElements) {
            newLookupElements.add(
                    myModelTypes.get(lookupElement.getLookupString()) != ModelType.DEFAULT ?
                            new IRenLookups.LookupWithProbability(lookupElement, myNameProbabilities, myModelTypes) :
                            new IRenLookups.Default(lookupElement));
        }
        return newLookupElements.toArray(lookupElements);
    }
}