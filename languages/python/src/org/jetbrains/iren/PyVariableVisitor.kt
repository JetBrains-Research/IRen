package org.jetbrains.iren

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTargetExpression
import org.jetbrains.iren.utils.RenameUtils

class PyVariableVisitor(private val holder: ProblemsHolder, private val isOnTheFly: Boolean) : PyElementVisitor() {
    override fun visitPyTargetExpression(node: PyTargetExpression) {
        try {
            RenameUtils.visitVariable(node, holder, isOnTheFly)
        } finally {
            super.visitPyTargetExpression(node)
        }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
        try {
            RenameUtils.visitVariable(node, holder, isOnTheFly)
        } finally {
            super.visitPyNamedParameter(node)
        }
    }
}