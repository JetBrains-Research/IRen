package org.jetbrains.iren.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.services.ConsistencyChecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

public class IRenLookupExpression extends MyLookupExpression {
    private final LinkedHashMap<String, Double> myNameProbabilities;

    public IRenLookupExpression(String name, @Nullable LinkedHashSet<String> names, @NotNull PsiNamedElement elementToRename, @Nullable PsiElement nameSuggestionContext, boolean shouldSelectAll, String advertisement, @NotNull LinkedHashMap<String, Double> nameProbabilities) {
        super(name, names, elementToRename, nameSuggestionContext, shouldSelectAll, advertisement);
        myNameProbabilities = nameProbabilities;
        @NotNull SmartPsiElementPointer<PsiNameIdentifierOwner> pointer =
                SmartPointerManager.createPointer((PsiNameIdentifierOwner) elementToRename);
        ConsistencyChecker.getInstance().rememberRenamedVariable(pointer);
    }

    @Override
    public LookupElement[] calculateLookupItems(ExpressionContext context) {
        LookupElement[] lookupElements = super.calculateLookupItems(context);
        List<LookupElement> newLookupElements = new ArrayList<>();
        for (LookupElement lookupElement : lookupElements) {
            newLookupElements.add(
                    myNameProbabilities.containsKey(lookupElement.getLookupString()) ?
                            new IRenLookups.NGram(lookupElement, myNameProbabilities) :
                            new IRenLookups.Default(lookupElement));
        }
        return newLookupElements.toArray(lookupElements);
    }
}