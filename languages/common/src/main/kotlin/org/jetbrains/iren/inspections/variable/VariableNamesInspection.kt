package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.services.NGramModelsUsabilityService
import org.jetbrains.iren.utils.ModelUtils

class VariableNamesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val language = holder.file.language
        val project = holder.project
        return if (ApplicationManager.getApplication().isUnitTestMode ||
            NGramModelsUsabilityService.getInstance(project).isUsable(ModelUtils().getName(project, language))
        ) LanguageSupporter.getVariableVisitor(language, holder) else PsiElementVisitor.EMPTY_VISITOR
    }

    override fun showDefaultConfigurationOptions(): Boolean {
        return false
    }

    override fun runForWholeFile(): Boolean {
        return true
    }
}