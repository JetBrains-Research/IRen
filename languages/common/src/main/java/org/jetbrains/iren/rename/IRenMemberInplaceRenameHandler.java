package org.jetbrains.iren.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer;
import org.jetbrains.annotations.NotNull;

public class IRenMemberInplaceRenameHandler extends MemberInplaceRenameHandler {
    @Override
    protected @NotNull MemberInplaceRenamer createMemberRenamer(@NotNull PsiElement element, @NotNull PsiNameIdentifierOwner elementToRename, @NotNull Editor editor) {
        return new IRenMemberInplaceRenamer(elementToRename, element, editor);
    }
}
