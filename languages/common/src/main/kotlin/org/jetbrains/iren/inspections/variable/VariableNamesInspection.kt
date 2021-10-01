package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.iren.utils.LanguageSupporter

class VariableNamesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return LanguageSupporter.getVariableVisitor(holder.file.language, holder)
    }

    override fun showDefaultConfigurationOptions(): Boolean {
        return false
    }
}