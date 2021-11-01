package org.jetbrains.astrid.inspections.ifstatement

import com.intellij.codeInspection.*
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.astrid.downloader.Downloader
import org.jetbrains.astrid.model.ModelFacade
import java.nio.file.Files

class IfStatementInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return IfStatementVisitor(holder)
    }

    private class IfStatementVisitor(private val holder: ProblemsHolder) : JavaElementVisitor() {
        override fun visitIfStatement(statement: PsiIfStatement?) {
            if (statement == null) return
            if (!Files.exists(Downloader.getModelPath())) return
            val condition = statement.condition ?: return
            // TODO: Implement more meaningful conditions
            if (condition.textLength > 100) {
                holder.registerProblem(condition, "Condition is too long", ProblemHighlightType.WEAK_WARNING,
                        ExtractIfStatementToMethod())
            }
            super.visitIfStatement(statement)
        }

    }

    class ExtractIfStatementToMethod : LocalQuickFix {
        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            if (descriptor.psiElement == null) return
            val currentMethod = descriptor.psiElement.parent.parent.parent as PsiMethod
            val parametersArray = currentMethod.parameterList.parameters
            var newMethodParameters = ""
            var newMethodCallArgs = ""
            val condition: String = descriptor.psiElement.text ?: ""
            for (parameter in parametersArray) {
                val parameterName: String = parameter.name ?: return
                if (condition.contains(parameterName)) {
                    if (newMethodParameters.isNotEmpty()) {
                        newMethodParameters += ", "
                        newMethodCallArgs += ", "
                    }
                    newMethodParameters += parameter.type.presentableText + " " + parameterName
                    newMethodCallArgs += parameterName
                }
            }
            val temporarySignature = "public boolean f() {"
            val newMethodBody = "($condition);"
            val classMethodNames = arrayListOf<String>()
            PsiTreeUtil.getParentOfType(descriptor.psiElement, PsiClass::class.java)?.methods?.forEach { m -> classMethodNames.add(m.name) }
            val methodNameSuggestions = ModelFacade().getSuggestions("$temporarySignature return $newMethodBody\n }")
            // Exclude name if class contains method with the same name
            classMethodNames.forEach { name -> methodNameSuggestions.removeName(name) }
            val newMethodName = methodNameSuggestions.names.get(0).first
            val newMethodText = "private boolean ${newMethodName}($newMethodParameters) { return $newMethodBody\n }"

            WriteCommandAction.runWriteCommandAction(project, addNewMethod(newMethodText, descriptor, project))
            if (descriptor.psiElement is PsiExpression) {
                val editor = DataManager.getInstance().dataContext.getData(PlatformDataKeys.EDITOR)
                descriptor.psiElement.delete()
                EditorModificationUtil.insertStringAtCaret(editor, "${newMethodName}($newMethodCallArgs)", true)
            }
        }

        private fun addNewMethod(newMethodText: String, descriptor: ProblemDescriptor, project: Project): Runnable {
            return Runnable {
                val facade = JavaPsiFacade.getInstance(project)
                val factory = facade.elementFactory
                val newPsiMethod = factory.createMethodFromText(newMethodText, null)
                PsiTreeUtil.getParentOfType(descriptor.psiElement, PsiClass::class.java)?.add(newPsiMethod)
                val editor = DataManager.getInstance().dataContext.getData(PlatformDataKeys.EDITOR)
                if (editor != null) {
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.document)
                }
            }
        }

        override fun getFamilyName(): String {
            return "If-statement extractor"
        }

    }

    override fun getDisplayName(): String {
        return "Extract long is-statement condition to new method"
    }

    override fun getGroupDisplayName(): String {
        return "Plugin id names suggesting"
    }

    override fun getShortName(): String {
        return "IfStatementInspection"
    }
}