package tools.modelsEvaluator

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {
    private val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    private val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override fun getCommandName(): String = "modelsEvaluator"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            val saveDir = args[2]
            val ngramContributorType = args[3]
            if (ngramContributorType == "global") trainGlobalNGramModelOn(dataset, javaSmallTrain)
            evaluateOn(dataset, javaSmallTest, Paths.get(saveDir), ngramContributorType)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            println("Not enough memory!")
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }

    private fun trainGlobalNGramModelOn(dataset: File, projectList: List<String>) {
        println("Training global NGram model...")
        var projectToClose: Project? = null
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            IdNamesSuggestingModelManager.getInstance().trainGlobalNGramModel(project, null, false)

            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectUtil.closeAndDispose(projectToClose)
        }
    }

    private fun evaluateOn(
        dataset: File,
        projectList: List<String>,
        dir: Path,
        ngramContributorType: String
    ) {
        println("Evaluating models...")
        var projectToClose: Project? = null
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            if (ngramContributorType == "project") {
                IdNamesSuggestingModelManager.getInstance().trainProjectNGramModel(project, null)
            }
            VarNamer.predict(project, dir, ngramContributorType)

            projectToClose = project
        }
        if (projectToClose != null) {
            ProjectUtil.closeAndDispose(projectToClose)
        }
    }
}
