package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.iren.api.LanguageSupporter
import org.jetbrains.iren.services.ModelManager
import org.jetbrains.iren.services.ModelStatsService

class VariableNamesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val language = holder.file.language
        return if (ModelStatsService.getInstance().isUsable(
                ModelManager.getName(holder.project, language)))
            LanguageSupporter.getVariableVisitor(language, holder) else PsiElementVisitor.EMPTY_VISITOR
    }

    override fun showDefaultConfigurationOptions(): Boolean {
        return false
    }
}