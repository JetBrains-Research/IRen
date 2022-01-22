package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.jetbrains.iren.RenameBundle

class RenameVariableQuickFix(
    private var variable: SmartPsiElementPointer<PsiNameIdentifierOwner>
) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val dataContext = DataManager.getInstance().dataContextFromFocusAsync.blockingGet(1) ?: return
        val editor = dataContext.getData(CommonDataKeys.EDITOR)

        RenameHandlerRegistry.getInstance()
            .getRenameHandler(dataContext)
            ?.invoke(project, editor, variable.containingFile, dataContext)
    }

    override fun getFamilyName(): String {
        return RenameBundle.message("inspection.family.name")
    }
}