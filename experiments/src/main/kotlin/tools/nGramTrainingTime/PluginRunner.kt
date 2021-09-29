package tools.nGramTrainingTime

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.iren.ModelTrainer
import org.jetbrains.iren.impl.NGramModelRunner
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

class PluginRunner : ApplicationStarter {
    private val javaSmallTrain = listOf(
        "cassandra",
        "elasticsearch", "gradle", "hibernate-orm",
        "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly",
        "libgdx", "hadoop"
    )

    override fun getCommandName(): String = "nGramTrainingTime"

    override fun main(args: Array<out String>) {
        try {
            val dataset = File(args[1])
            val saveDir = args[2]
            trainOn(dataset, javaSmallTrain, Paths.get(saveDir))
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

    private fun trainOn(
        dataset: File,
        projectList: List<String>,
        dir: Path
    ) {
        println("Evaluating models...")
        var projectToClose: Project? = null
        val timeFile: File = dir.resolve("train_time.json").toFile()
        timeFile.parentFile.mkdir()
        timeFile.createNewFile()
        val projectTime = HashMap<String, List<Long>>()
        for (projectDir in projectList) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            val trainingTime = ArrayList<Long>()
            for (i in 1..5) {
                val start = Instant.now()
                ModelTrainer.learnProject(NGramModelRunner(true), project, null)
                trainingTime.add(Duration.between(start, Instant.now()).seconds)
            }
            projectTime.put(projectDir, trainingTime)
            if (projectToClose != null) {
                ProjectManager.getInstance().closeAndDispose(projectToClose)
            }
            projectToClose = project
        }
        timeFile.writeText(ObjectMapper().writeValueAsString(projectTime))
    }
}
