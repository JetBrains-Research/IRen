package org.jetbrains.iren

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiVariable
import org.jetbrains.iren.utils.RenameUtils

class JavaVariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
    override fun visitVariable(variable: PsiVariable) {
        try {
            RenameUtils.visitVariable(variable, holder)
        } finally {
            super.visitVariable(variable)
        }
    }
}