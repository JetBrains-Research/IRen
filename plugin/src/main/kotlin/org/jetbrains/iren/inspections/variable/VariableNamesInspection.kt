package org.jetbrains.iren.inspections.variable

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.iren.rename.MyMemberInplaceRenamer

class VariableNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return VariableVisitor(holder)
    }

    class VariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitVariable(variable: PsiVariable?) {
            if (variable == null) return
            val predictions = thereIsBetterName(variable)
            if (predictions.isNotEmpty()) {
                holder.registerProblem(
                    variable.nameIdentifier ?: variable,
                    "There are suggestions for variable name",
                    ProblemHighlightType.WEAK_WARNING,
                    RenameMethodQuickFix(variable.createSmartPointer(), predictions)
                )
            }
            super.visitVariable(variable)
        }

        private fun thereIsBetterName(variable: PsiVariable): LinkedHashMap<String, Double> {
            val predictions = PredictionsStorage.getInstance().getPrediction(variable.createSmartPointer()) ?: LinkedHashMap()
            val threshold = predictions[variable.name] ?: return predictions
            return predictions.filterValues { v -> v > threshold }.toMap(LinkedHashMap())
        }
    }

    class RenameMethodQuickFix(
        private var variable: SmartPsiElementPointer<PsiVariable>,
        private var predictions: LinkedHashMap<String, Double>
    ) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
            val inplaceRefactoring =
                MyMemberInplaceRenamer(variable.element!!, null, editor)
            inplaceRefactoring.performInplaceRefactoring(predictions)
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

}