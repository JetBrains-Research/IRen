package org.jetbrains.astrid.utils

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import org.jetbrains.id.names.suggesting.utils.PsiUtils.findReferences
import java.util.concurrent.atomic.AtomicReference

object PsiUtils {
    /**
     * Extracts method signature and body, then concatenates them
     */
    fun getMethodBody(method: PsiMethod): String {
        val methodBody = method.body ?: return ""
        val space = " "
        val modifierList: PsiModifierList = method.modifierList
        val parameters = method.parameterList.parameters
        val methodSignature = StringBuilder(256)
        if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            methodSignature.append(PsiModifier.PUBLIC).append(space)
        } else if (modifierList.hasModifierProperty(PsiModifier.PRIVATE)) {
            methodSignature.append(PsiModifier.PRIVATE).append(space)
        }
        if (modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            methodSignature.append(PsiModifier.STATIC).append(space)
        }
        val returnType = method.returnType?.presentableText
        methodSignature.append(returnType).append(space).append(method.name).append('(')
        for (i in parameters.indices) {
            if (i != 0) {
                methodSignature.append(',')
            }
            val parameterName: String? = parameters[i].name
            val parameterType: PsiType = parameters[i].type
            val parameterTypeText = parameterType.presentableText
            methodSignature.append(parameterTypeText).append(space).append(parameterName)
        }
        methodSignature.append(')')
        return methodSignature.toString() + space + methodBody.text
    }

    fun caretInsideMethodBlock(method: PsiMethod): Boolean {
        val methodTextRange = method.textRange
        val offset = getOffset(method)
        return if (offset != null) {
            (offset > methodTextRange.startOffset) && (offset < methodTextRange.endOffset)
        } else {
            false
        }
    }

    fun caretInsideVariable(variable: PsiVariable): Boolean {
        val offset = getOffset(variable)
        return if (offset != null) {
            findReferences(variable, variable.containingFile).anyMatch { reference: PsiReference ->
                offset >= reference.rangeInElement.startOffset &&
                        offset <= reference.rangeInElement.endOffset
            }
        } else {
            false
        }
    }

    private fun getOffset(element: PsiElement): Int? {
        val editor = AtomicReference<Editor?>(null)
        ReadAction.nonBlocking { editor.set(FileEditorManager.getInstance(element.project).selectedTextEditor) }
        return editor.get()?.caretModel?.offset
    }

    fun hasSuperMethod(method: PsiMethod): Boolean {
        return method.findSuperMethods().isNotEmpty()
    }

    fun executeWriteAction(project: Project, file: PsiFile, body: () -> Unit) {
        object : WriteCommandAction.Simple<Any>(project, file) {
            override fun run() {
                body()
            }
        }.execute()
    }

}