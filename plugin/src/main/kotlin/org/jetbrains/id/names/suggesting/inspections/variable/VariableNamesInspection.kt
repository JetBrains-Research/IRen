package org.jetbrains.id.names.suggesting.inspections.variable

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.suggested.createSmartPointer
import org.jetbrains.id.names.suggesting.IdNamesSuggestingService
import org.jetbrains.id.names.suggesting.ModifiedMemberInplaceRenamer

class VariableNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return VariableVisitor(holder)
    }

    class VariableVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        private val probabilityCutoff: Double = 0.001

        override fun visitVariable(variable: PsiVariable?) {
            when (variable) {
                null -> return
                else -> {
                    val probability = IdNamesSuggestingService.getInstance().getVariableNameProbability(variable)
                    if (probability < probabilityCutoff) {
                        holder.registerProblem(
                            variable.nameIdentifier ?: variable,
                            "There are suggestions for variable name",
                            ProblemHighlightType.WEAK_WARNING,
                            RenameMethodQuickFix(variable.createSmartPointer())
                        )
                    }
                    super.visitVariable(variable)
                }
            }
        }
    }

    class RenameMethodQuickFix(private var variable: SmartPsiElementPointer<PsiVariable>) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!
            val inplaceRefactoring = ModifiedMemberInplaceRenamer(variable.element!!, null, editor)
            inplaceRefactoring.performInplaceRefactoring(IdNamesSuggestingService.getInstance()
                    .suggestVariableName(variable.element!!))
        }


        override fun getFamilyName(): String {
            return "Show variable name suggestions"
        }

    }

    override fun getDisplayName(): String {
        return "Show variable name suggestions"
    }

    override fun getGroupDisplayName(): String {
        return "Plugin id names suggesting"
    }

    override fun getShortName(): String {
        return "VariableNamesInspection"
    }

}