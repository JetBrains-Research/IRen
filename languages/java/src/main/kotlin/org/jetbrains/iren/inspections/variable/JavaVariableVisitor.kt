package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.createSmartPointer
import org.jetbrains.iren.IRenBundle
import org.jetbrains.iren.ModelStatsService
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor
import org.jetbrains.iren.rename.IRenMemberInplaceRenamer

class JavaVariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
    override fun visitVariable(variable: PsiVariable?) {
        if (variable == null ||
            !ModelStatsService.getInstance().isUsable(ProjectVariableNamesContributor::class.java, variable.project)
        ) return
        try {
            val predictions = PredictionsStorage.getInstance().getPrediction(variable.createSmartPointer())
            if (isGoodPredictionList(variable, predictions)) {
                holder.registerProblem(
                    variable.nameIdentifier ?: variable,
                    IRenBundle.message("inspection.description.template"),
                    ProblemHighlightType.WEAK_WARNING,
                    RenameMethodQuickFix(variable.createSmartPointer())
                )
            }
        } finally {
            super.visitVariable(variable)
        }
    }
}

class RenameMethodQuickFix(
    private var variable: SmartPsiElementPointer<PsiVariable>
) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
        val inplaceRefactoring =
            IRenMemberInplaceRenamer(variable.element!!, null, editor)
        inplaceRefactoring.performInplaceRename()
    }

    override fun getFamilyName(): String {
        return IRenBundle.message("inspection.family.name")
    }
}