package tools.modelsEvaluatorApi

import com.intellij.ide.impl.ProjectUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.python.PythonLanguage
import org.jetbrains.iren.ModelBuilder
import org.jetbrains.iren.ModelManager
import org.jetbrains.iren.impl.NGramModelRunner
import org.jetbrains.iren.settings.AppSettingsState
import org.jetbrains.iren.utils.LanguageSupporter
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import kotlin.system.exitProcess

open class PluginRunner : ApplicationStarter {
    private lateinit var dataset: File
    protected lateinit var saveDir: Path
    protected lateinit var supporter: LanguageSupporter
    protected lateinit var ngramType: String
    private lateinit var varNamer: VarNamer

    private val ngramTypes = listOf("BiDirectional", "OneDirectional")

    protected open val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    protected open val javaSmallTest =
        listOf("libgdx", "hadoop")

    //        listOf("TestProject")
    private val projectList: List<String>? = null

    override fun getCommandName(): String = "modelsEvaluator"

    override fun main(args: Array<out String>) {
        try {
            dataset = File(args[1])
            saveDir = Paths.get(args[2])
            supporter = LanguageSupporter.getInstance(
                when (args[3].toLowerCase()) {
                    "java" -> JavaLanguage.INSTANCE
                    "python" -> PythonLanguage.INSTANCE
                    "kotlin" -> KotlinLanguage.INSTANCE
                    else -> throw AssertionError("Unknown language")
                }
            )!!
            ngramType = args[4]
            assert(ngramTypes.contains(ngramType))
            varNamer = createVarNamer()
            evaluateOn()
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

    open fun createVarNamer(): VarNamer {
        return VarNamer(saveDir, supporter, ngramType)
    }

//    private fun trainGlobalNGramModelOn(dataset: File, projectList: List<String>) {
//        println("Training global NGram model...")
//        var projectToClose: Project? = null
//        for (projectDir in projectList) {
//            val projectPath = dataset.resolve(projectDir)
//            println("Opening project $projectDir...")
//            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
//
//            ModelBuilder.trainGlobalNGramModel(project, null, false)
//
//            projectToClose = project
//        }
//        if (projectToClose != null) {
//            ProjectManager.getInstance().closeAndDispose(projectToClose)
//        }
//    }

    private fun evaluateOn() {
        println("Evaluating models...")
        var projectToClose: Project? = null
        val timeSpentFile: File = saveDir.resolve("timeSpent.csv").toFile()
        timeSpentFile.parentFile.mkdirs()
        if (timeSpentFile.createNewFile()) {
            FileOutputStream(timeSpentFile, true).bufferedWriter()
                .use { it.write("Project,TrainingTime,EvaluationTime\n") }
        }
        for (projectDir in projectList
            ?: listOf(*dataset.list { file, name -> file.isDirectory && !name.startsWith(".") } ?: return)) {
            val projectPath = dataset.resolve(projectDir)
            println("Opening project $projectDir...")
            val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue

            if (ngramType == "OneDirectional") {
                NGramModelRunner.DEFAULT_BIDIRECTIONAL = false
            }

            try {
                var start = Instant.now()
                val settings = AppSettingsState.getInstance()
                settings.maxTrainingTime = 420
                settings.vocabularyCutOff = 0
                ModelBuilder.trainProjectNGramModelWithSupporter(
                    project,
                    supporter,
                    null,
                    false
                )
                val trainingTime = Duration.between(start, Instant.now())
                FileOutputStream(timeSpentFile, true).bufferedWriter().use {
                    it.write("${project.name},$trainingTime,")
                }

                start = Instant.now()
                if (varNamer.predict(project)) {
                    val evaluationTime = Duration.between(start, Instant.now())

                    FileOutputStream(timeSpentFile, true).bufferedWriter().use {
                        it.write("$evaluationTime")
                    }
                }
            } finally {
                FileOutputStream(timeSpentFile, true).bufferedWriter().use {
                    it.write("\n")
                }
                ModelManager.getInstance().deleteProjectModelRunners(project)
                projectToClose = project
            }
        }
        if (projectToClose != null) {
            ProjectManager.getInstance().closeAndDispose(projectToClose)
        }
    }
}
