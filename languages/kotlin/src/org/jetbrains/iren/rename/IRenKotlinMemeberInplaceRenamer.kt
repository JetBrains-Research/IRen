package org.jetbrains.iren.rename

import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer
import org.jetbrains.kotlin.idea.base.psi.unquoteKotlinIdentifier
import org.jetbrains.kotlin.psi.KtObjectDeclaration

class IRenKotlinMemeberInplaceRenamer : IRenMemberInplaceRenamer {
    constructor(
        elementToRename: PsiNamedElement,
        substitutedElement: PsiElement?,
        editor: Editor,
        currentName: String,
        oldName: String,
    ) : super(elementToRename, substitutedElement, editor, currentName, oldName)

    constructor(
        elementToRename: PsiNamedElement,
        editor: Editor,
    ) : super(elementToRename, editor)

    override fun isIdentifier(newName: String?, language: Language?): Boolean {
        if (newName == "" && (variable as? KtObjectDeclaration)?.isCompanion() == true) return true
        return super.isIdentifier(newName, language)
    }

    override fun acceptReference(reference: PsiReference): Boolean {
        val refElement = reference.element
        val textRange = reference.rangeInElement
        val referenceText = refElement.text.substring(textRange.startOffset, textRange.endOffset).unquoteKotlinIdentifier()
        return referenceText == myElementToRename.name
    }

    override fun startsOnTheSameElement(handler: RefactoringActionHandler?, element: PsiElement?): Boolean {
        return variable == element && (handler is IRenMemberInplaceRenameHandler)
    }

    override fun createInplaceRenamerToRestart(
        variable: PsiNamedElement,
        editor: Editor,
        initialName: String,
    ): VariableInplaceRenamer {
        return IRenKotlinMemeberInplaceRenamer(variable, substituted, editor, initialName, myOldName)
    }
}