package org.jetbrains.iren.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.IRenSuggestingService;
import org.jetbrains.iren.api.IRenIntentionBase;
import org.jetbrains.iren.rename.MyMemberInplaceRenamer;

public class SuggestVariableNamesIntention extends IRenIntentionBase<PsiVariable> {
    @Override
    public @NotNull String getText() {
        return IRenBundle.message("intention.text");
    }

    @Override
    protected void processIntention(@NotNull Project project, @NotNull Editor editor, @NotNull PsiVariable variable) {
        MyMemberInplaceRenamer inplaceRefactoring = new MyMemberInplaceRenamer(variable, editor);
        inplaceRefactoring.performInplaceRefactoring(IRenSuggestingService.getInstance()
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
