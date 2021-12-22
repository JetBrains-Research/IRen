// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.iren.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenamer
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler
import org.jetbrains.kotlin.psi.KtPrimaryConstructor

class IRenKotlinMemberInplaceRenameHandler : MemberInplaceRenameHandler() {
    private fun PsiElement.substitute(): PsiElement {
        if (this is KtPrimaryConstructor) return getContainingClassOrObject()
        return this
    }

    override fun createMemberRenamer(
        element: PsiElement,
        elementToRename: PsiNameIdentifierOwner,
        editor: Editor,
    ): MemberInplaceRenamer {
        val currentElementToRename = elementToRename.substitute() as PsiNameIdentifierOwner
        val nameIdentifier = currentElementToRename.nameIdentifier

        // Move caret if constructor range doesn't intersect with the one of the containing class
        val offset = editor.caretModel.offset
        val editorPsiFile = PsiDocumentManager.getInstance(element.project).getPsiFile(editor.document)
        if (nameIdentifier != null && editorPsiFile == elementToRename.containingFile && elementToRename is KtPrimaryConstructor && offset !in nameIdentifier.textRange && offset in elementToRename.textRange) {
            editor.caretModel.moveToOffset(nameIdentifier.textOffset)
        }

        val currentName = nameIdentifier?.text ?: ""
        return IRenKotlinMemeberInplaceRenamer(currentElementToRename, element, editor, currentName, currentName)
    }

    override fun isAvailable(element: PsiElement?, editor: Editor, file: PsiFile): Boolean {
        if (!editor.settings.isVariableInplaceRenameEnabled) return false
        val currentElement = element?.substitute() as? PsiNameIdentifierOwner ?: return false
        return currentElement.nameIdentifier != null && !KotlinVariableInplaceRenameHandler.isInplaceRenameAvailable(
            currentElement)
    }
}
