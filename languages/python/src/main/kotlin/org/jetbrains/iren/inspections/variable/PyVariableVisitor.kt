package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyTargetExpression
import org.jetbrains.iren.IRenBundle
import org.jetbrains.iren.ModelManager
import org.jetbrains.iren.ModelStatsService
import org.jetbrains.iren.rename.IRenVariableInplaceRenamer

class PyVariableVisitor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyTargetExpression(node: PyTargetExpression) {
        if (!ModelStatsService.getInstance().isUsable(
                ModelManager.getName(node.project, node.language)
            )
        ) return
        try {
            if (ConsistencyChecker.getInstance().isInconsistent(node.createSmartPointer())) {
                holder.registerProblem(
                    node.nameIdentifier ?: node,
                    IRenBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    RenameMethodQuickFix(node.createSmartPointer())
                )
            }
        } finally {
            super.visitPyTargetExpression(node)
        }
    }
}

class RenameMethodQuickFix(
    private var variable: SmartPsiElementPointer<PsiNameIdentifierOwner>
) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        val inplaceRefactoring = IRenVariableInplaceRenamer(variable.element!!, editor)
        inplaceRefactoring.performInplaceRename()
    }

    override fun getFamilyName(): String {
        return IRenBundle.message("inspection.family.name")
    }
}