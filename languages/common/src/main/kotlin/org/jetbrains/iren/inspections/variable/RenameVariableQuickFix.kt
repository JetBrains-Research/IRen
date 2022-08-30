package org.jetbrains.iren.inspections.variable

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.jetbrains.iren.utils.RenameBundle

// Copied from org.jetbrains.kotlin.idea.quickfix.RenameIdentifierFix
class RenameVariableQuickFix : LocalQuickFix {
    override fun getName() = RenameBundle.message("inspection.family.name")
    override fun getFamilyName() = name

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return
        val editorManager = FileEditorManager.getInstance(project)
        val fileEditor = editorManager.getSelectedEditor(file.virtualFile) ?: return renameWithoutEditor(element)
        val dataContext = DataManager.getInstance().getDataContext(fileEditor.component)
        val renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)

        val editor = editorManager.selectedTextEditor
        if (editor != null) {
            renameHandler?.invoke(project, editor, file, dataContext)
        } else {
            val elementToRename = getElementToRename(element) ?: return
            renameHandler?.invoke(project, arrayOf(elementToRename), dataContext)
        }
    }

    private fun getElementToRename(element: PsiElement): PsiElement? = element.parent

    private fun renameWithoutEditor(element: PsiElement) {
        val elementToRename = getElementToRename(element) ?: return
        val factory = RefactoringFactory.getInstance(element.project)
        val renameRefactoring = factory.createRename(elementToRename, null, true, true)
        renameRefactoring.run()
    }
}