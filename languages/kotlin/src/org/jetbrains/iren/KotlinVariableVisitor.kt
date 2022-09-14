package org.jetbrains.iren

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.iren.utils.RenameUtils
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KotlinVariableVisitor(private val holder: ProblemsHolder, private val isOnTheFly: Boolean) : KtVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        try {
            RenameUtils.visitVariable(property, holder, isOnTheFly)
        } finally {
            super.visitProperty(property)
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        try {
            RenameUtils.visitVariable(parameter, holder, isOnTheFly)
        } finally {
            super.visitParameter(parameter)
        }
    }
}