package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyTargetExpression
import com.jetbrains.python.refactoring.PyRefactoringProvider
import org.jetbrains.iren.utils.RenameUtils

class PyVariableVisitor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyTargetExpression(node: PyTargetExpression) {
        try {
            if (PyRefactoringProvider().isInplaceRenameAvailable(node, null)) {
                RenameUtils.visitVariable(node, holder)
            }
        } finally {
            super.visitPyTargetExpression(node)
        }
    }
}