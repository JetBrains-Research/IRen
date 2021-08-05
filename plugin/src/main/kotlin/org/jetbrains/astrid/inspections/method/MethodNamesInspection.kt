package org.jetbrains.astrid.inspections.method

import com.intellij.codeInspection.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import org.jetbrains.astrid.actions.SuggestionListPopupStep
import org.jetbrains.astrid.downloader.Downloader
import org.jetbrains.astrid.inspections.Suggestion
import org.jetbrains.astrid.model.ModelFacade
import org.jetbrains.astrid.utils.PsiUtils
import org.jetbrains.astrid.utils.PsiUtils.hasSuperMethod
import java.nio.file.Files

class MethodNamesInspection : AbstractBaseJavaLocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return MethodVisitor(holder)
    }

    class MethodVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitMethod(method: PsiMethod?) {
            when {
                method == null -> return
                method.body == null -> return
                method.isConstructor -> return
                hasSuperMethod(method) -> return
                !Files.exists(Downloader.getModelPath()) -> return
                else -> {
                    val suggestions = ModelFacade().getSuggestions(method)
                    if (suggestions.names.isNotEmpty()
                            && !suggestions.containsName(method.name)) {
                        holder.registerProblem(method.nameIdentifier ?: method,
                                "There are suggestions for method name",
                                ProblemHighlightType.WEAK_WARNING,
                                RenameMethodQuickFix(suggestions))
                    }
                    super.visitMethod(method)
                }
            }
        }
    }

    class RenameMethodQuickFix(private var suggestions: Suggestion) : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            this.suggestions.addName("Suppress on this method")
            val file = descriptor.psiElement.containingFile
            val editor = FileEditorManager.getInstance(project).selectedTextEditor!!

            PsiUtils.executeWriteAction(project, file) {
                val listPopup = JBPopupFactory.getInstance().createListPopup(
                        SuggestionListPopupStep("Suggestions", suggestions, editor, file)
                )
                listPopup.showInBestPositionFor(editor)
            }
        }

        override fun getFamilyName(): String {
            return "Show method name suggestions"
        }

    }

    override fun getDisplayName(): String {
        return "Show method name suggestions"
    }

    override fun getGroupDisplayName(): String {
        return "Plugin id names suggesting"
    }

    override fun getShortName(): String {
        return "MethodNamesInspection"
    }

}