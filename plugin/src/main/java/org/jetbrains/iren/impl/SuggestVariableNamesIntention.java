package org.jetbrains.iren.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IdNamesSuggestingBundle;
import org.jetbrains.iren.IdNamesSuggestingService;
import org.jetbrains.iren.rename.MyMemberInplaceRenamer;
import org.jetbrains.iren.api.SuggestIdNamesIntentionBase;

public class SuggestVariableNamesIntention extends SuggestIdNamesIntentionBase<PsiVariable> {
    @Override
    public @NotNull String getText() {
        return IdNamesSuggestingBundle.message("intention.text");
    }

    @Override
    protected void processIntention(@NotNull Project project, @NotNull Editor editor, @NotNull PsiVariable variable) {
        MyMemberInplaceRenamer inplaceRefactoring = new MyMemberInplaceRenamer(variable, editor);
        inplaceRefactoring.performInplaceRefactoring(IdNamesSuggestingService.getInstance()
                                                                             .suggestVariableName(variable));
    }

    @Override
    protected @Nullable PsiVariable getIdentifierOwner(@Nullable PsiElement element) {
        if (element instanceof PsiIdentifier) {
            element = element.getParent();
            if (element instanceof PsiVariable) {
                return (PsiVariable) element;
            }
            if (element instanceof PsiReferenceExpression) {
                element = ((PsiReferenceExpression) element).resolve();
                if (element instanceof PsiVariable) {
                    return (PsiVariable) element;
                }
            }
        }
        return null;
    }
}
