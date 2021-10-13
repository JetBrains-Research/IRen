package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.iren.IRenBundle
import org.jetbrains.iren.services.ConsistencyChecker
import org.jetbrains.iren.services.ModelManager
import org.jetbrains.iren.services.ModelStatsService

class JavaVariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
    override fun visitVariable(variable: PsiVariable?) {
        if (variable == null ||
            !ModelStatsService.getInstance().isUsable(
                ModelManager.getName(variable.project, variable.language)
            )
        ) return
        try {
            val pointer: SmartPsiElementPointer<PsiNameIdentifierOwner> = variable.createSmartPointer()
            if (ConsistencyChecker.getInstance().isInconsistent(variable)) {
                holder.registerProblem(
                    variable.nameIdentifier ?: variable,
                    IRenBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    RenameVariableQuickFix(pointer)
                )
            }
        } finally {
            super.visitVariable(variable)
        }
    }
}