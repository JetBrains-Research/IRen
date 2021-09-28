// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.iren.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.core.unquote
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler

open class IRenKotlinVariableInplaceRenameHandler : KotlinVariableInplaceRenameHandler() {
    protected open class RenamerImpl(
        elementToRename: PsiNamedElement,
        editor: Editor,
        currentName: String,
        oldName: String
    ) : IRenVariableInplaceRenamer(elementToRename, editor, editor.project!!, currentName, oldName) {

        override fun acceptReference(reference: PsiReference): Boolean {
            val refElement = reference.element
            val textRange = reference.rangeInElement
            val referenceText = refElement.text.substring(textRange.startOffset, textRange.endOffset).unquote()
            return referenceText == myElementToRename.name
        }

        override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
            return variable == element && (handler is IRenVariableInplaceRenameHandler)
        }

        override fun createInplaceRenamerToRestart(variable: PsiNamedElement, editor: Editor, initialName: String): VariableInplaceRenamer {
            return RenamerImpl(variable, editor, initialName, myOldName)
        }
    }

    override fun createRenamer(elementToRename: PsiElement, editor: Editor): VariableInplaceRenamer? {
        val currentElementToRename = elementToRename as PsiNameIdentifierOwner
        val currentName = currentElementToRename.nameIdentifier?.text ?: ""
        return RenamerImpl(currentElementToRename, editor, currentName, currentName)
    }
}