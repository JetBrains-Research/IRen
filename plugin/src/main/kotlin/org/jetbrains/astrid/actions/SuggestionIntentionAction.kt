package org.jetbrains.astrid.actions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.astrid.downloader.Downloader
import org.jetbrains.astrid.inspections.Suggestion
import org.jetbrains.astrid.model.ModelFacade
import org.jetbrains.astrid.utils.FileUtils
import org.jetbrains.astrid.utils.PsiUtils.executeWriteAction
import java.net.URL
import java.nio.file.Files

class SuggestionIntentionAction : IntentionAction {

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val offset: Int = editor.caretModel.offset
        val psiMethod = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiMethod::class.java) ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Method name suggestions", true) {
            override fun run(indicator: ProgressIndicator) {
                var suggestionsList: Suggestion? = null
//                if (!Files.exists(Downloader.getModelPath())) {
//                    Downloader.getPluginPath().toFile().mkdir()
//                    Downloader.downloadArchive(URL(Downloader.modelLink), Downloader.getArchivePath(),
//                            ProgressManager.getInstance().progressIndicator)
//                    if (indicator.isCanceled) return
//                    ProgressManager.getInstance().progressIndicator.text = "Extracting archive"
//                    FileUtils.unzip(Downloader.getArchivePath().toString(), Downloader.getModelPath().toString())
//                }

                runReadAction {
                    indicator.text = "Generating method name suggestions"
                    suggestionsList = ModelFacade().getSuggestions(psiMethod)
                }
                if (suggestionsList == null) return
                executeWriteAction(project, file) {
                    val listPopup = JBPopupFactory.getInstance().createListPopup(
                            SuggestionListPopupStep("Suggestions", suggestionsList!!, editor, file)
                    )
                    listPopup.showInBestPositionFor(editor)
                }
            }
        })
    }


    override fun getText(): String {
        return "Generate suggestions"
    }

    override fun getFamilyName(): String {
        return text
    }

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        //TODO("Need to implement conditions")
        return true
    }

    override fun startInWriteAction(): Boolean {
        return true
    }

}