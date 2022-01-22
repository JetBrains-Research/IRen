package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.services.ModelsUsabilityService
import org.jetbrains.iren.utils.ModelUtils

class VariableNamesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val language = holder.file.language
        return if (ModelsUsabilityService.getInstance().isUsable(
                ModelUtils.getName(holder.project, language)))
            LanguageSupporter.getVariableVisitor(language, holder) else PsiElementVisitor.EMPTY_VISITOR
    }

    override fun showDefaultConfigurationOptions(): Boolean {
        return false
    }
}