package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.iren.utils.RenameUtils
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

class KotlinVariableVisitor(private val holder: ProblemsHolder) : KtVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        try {
            RenameUtils.visitVariable(property, holder)
        } finally {
            super.visitProperty(property)
        }
    }

    override fun visitParameter(parameter: KtParameter) {
        try {
            RenameUtils.visitVariable(parameter, holder)
        } finally {
            super.visitParameter(parameter)
        }
    }
}