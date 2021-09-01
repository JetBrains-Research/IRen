package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.*
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.iren.ModelStatsService
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor
import org.jetbrains.iren.rename.MyMemberInplaceRenamer

class VariableNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return VariableVisitor(holder)
    }

    class VariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitVariable(variable: PsiVariable?) {
            if (variable == null ||
                !ModelStatsService.getInstance().isUsable(ProjectVariableNamesContributor::class.java, variable.project)
            ) return
            try {
                val predictions = PredictionsStorage.getInstance().getPrediction(variable.createSmartPointer())
                if (isGoodPredictionList(variable, predictions)) {
                    holder.registerProblem(
                        variable.nameIdentifier ?: variable,
                        "There are suggestions for variable name",
                        ProblemHighlightType.WEAK_WARNING,
                        RenameMethodQuickFix(variable.createSmartPointer())
                    )
                }
            } finally {
                super.visitVariable(variable)
            }
        }

        private fun isGoodPredictionList(variable: PsiVariable, predictions: LinkedHashMap<String, Double>): Boolean {
            val varThreshold = predictions[variable.name]
            val vocabThreshold = predictions[Vocabulary.unknownCharacter]
            return (if (varThreshold != null) predictions.filterValues { v -> v > varThreshold } else predictions).size >= 5 &&
                    (if (vocabThreshold != null) predictions.filterValues { v -> v > vocabThreshold } else predictions).isNotEmpty()
        }
    }

    class RenameMethodQuickFix(
        private var variable: SmartPsiElementPointer<PsiVariable>
    ) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
            val inplaceRefactoring = MyMemberInplaceRenamer(variable.element!!, null, editor)
            inplaceRefactoring.performInplaceRefactoring(PredictionsStorage.getInstance().getPrediction(variable))
        }


        override fun getFamilyName(): String {
            return "Show variable name suggestions"
        }

    }

    override fun getDisplayName(): String {
        return "Show variable name suggestions"
    }

    override fun getGroupDisplayName(): String {
        return "IRen plugin"
    }

    override fun getShortName(): String {
        return "VariableNamesInspection"
    }

    override fun isEnabledByDefault(): Boolean {
        return true
    }
}