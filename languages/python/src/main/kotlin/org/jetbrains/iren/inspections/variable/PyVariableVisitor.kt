package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemsHolder
import com.jetbrains.python.psi.PyElementVisitor
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyTargetExpression
import org.jetbrains.iren.utils.RenameUtils

class PyVariableVisitor(private val holder: ProblemsHolder) : PyElementVisitor() {
    override fun visitPyTargetExpression(node: PyTargetExpression) {
        try {
            RenameUtils.visitVariable(node, holder)
        } finally {
            super.visitPyTargetExpression(node)
        }
    }

    override fun visitPyNamedParameter(node: PyNamedParameter) {
        try {
            RenameUtils.visitVariable(node, holder)
        } finally {
            super.visitPyNamedParameter(node)
        }
    }
}