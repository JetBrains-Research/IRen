package org.jetbrains.iren.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IRenVariableInplaceRenameHandler extends VariableInplaceRenameHandler {
    @Override
    protected @Nullable VariableInplaceRenamer createRenamer(@NotNull PsiElement elementToRename, @NotNull Editor editor) {
        return new IRenVariableInplaceRenamer((PsiNamedElement) elementToRename, editor);
    }
}
