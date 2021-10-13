package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
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
                    RenameVariableQuickFix(node.createSmartPointer())
                )
            }
        } finally {
            super.visitPyTargetExpression(node)
        }
    }
}