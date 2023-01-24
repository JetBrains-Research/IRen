package org.jetbrains.astrid.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.RefactoringFactory
import org.jetbrains.astrid.inspections.Suggestion
import org.jetbrains.astrid.stats.RenameMethodStatistics

class SuggestionListPopupStep(
        aTitle: String, private val aValues: Suggestion, private var editor: Editor, private val psiFile: PsiFile
) : BaseListPopupStep<Pair<String, Double>>(aTitle, aValues.names.toMutableList()) {

    private var selectedMethodName: Pair<String, Double> = Pair("", 0.0)

    override fun onChosen(selectedValue: Pair<String, Double>, finalChoice: Boolean): PopupStep<*>? {
        selectedMethodName = selectedValue
        return super.onChosen(selectedValue, finalChoice)
    }

    private fun doRenameMethodRefactoring(selectedValue: Pair<String, Double>) {
        val elementAt = psiFile.findElementAt(editor.caretModel.offset) ?: return
        if (selectedMethodName.first == "Suppress on this method") {
            RenameMethodStatistics.ignoreCount(aValues.getScores(selectedValue.first))
            return
        }
        val refactoringFactory = RefactoringFactory.getInstance(editor.project)
        val rename = refactoringFactory.createRename(findNamedElement(elementAt), selectedValue.first)
        val usages = rename.findUsages()
        RenameMethodStatistics.applyCount(selectedValue.second)
        rename.doRefactoring(usages)
/*        StatsSender(FilePathProvider(),
                RequestService()).sendStatsData(LogEvent(RenameMethodStatistics.getInstance().state).toString())*/
    }

    private fun findNamedElement(element: PsiElement): PsiElement {
        when (element) {
            is PsiNamedElement -> return element
            else -> return findNamedElement(element.parent)
        }
    }

    override fun getFinalRunnable(): Runnable? {
        return Runnable { doRenameMethodRefactoring(selectedMethodName) }
    }

    override fun getTextFor(value: Pair<String, Double>): String {
        return value.first
    }
}
