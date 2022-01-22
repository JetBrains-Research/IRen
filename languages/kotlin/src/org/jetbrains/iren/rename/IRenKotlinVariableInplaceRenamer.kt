package org.jetbrains.iren.rename

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.core.unquote

class IRenKotlinVariableInplaceRenamer(
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
        return IRenKotlinVariableInplaceRenamer(variable, editor, initialName, myOldName)
    }
}