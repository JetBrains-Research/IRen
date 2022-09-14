package experiments.buildDataset

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.extensions.python.toPsi
import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.addText
import java.io.File
import java.io.FileOutputStream

class DatasetPluginRunner : PluginRunner() {
    override fun getCommandName(): String = "BuildDataset"

    override fun evaluate() {
        println("Building dataset...")
        var projectToClose: Project? = null
        val datsetFile: File = saveDir.resolve("python.txt").toFile()
        datsetFile.parentFile.mkdirs()
        datsetFile.createNewFile()
        FileOutputStream(datsetFile).close()
        val files = (projectList ?: (dataset.list { file, name ->
            file.isDirectory && !name.startsWith(".")
        } ?: return)
            .asSequence()
            .sorted()
            .toList())
        for (projectDir in files) {
            val projectPath = dataset.resolve(projectDir)
            if (!projectPath.isDirectory) continue
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
            try {
                val allFiles = FileTypeIndex.getFiles(
                    supporter.fileType,
                    GlobalSearchScope.projectScope(project)
                ).sortedBy { it.path }
                for (file in allFiles) {
                    addText(datsetFile, "$projectDir | ${supporter.getDOBFContext(
                        PsiTreeUtil.findChildOfType(file.toPsi(project), PsiNameIdentifierOwner::class.java)?: continue)?.tokens?.joinToString(" ")}\n")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                projectToClose = project
            }
        }
        if (projectToClose != null) {
            ProjectManager.getInstance().closeAndDispose(projectToClose)
        }
    }
}
