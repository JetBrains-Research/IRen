package tools.modelsEvaluatorApi

import com.intellij.ide.impl.ProjectUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.python.PythonLanguage
import org.jetbrains.iren.api.LanguageSupporter
import org.jetbrains.iren.ngram.ModelBuilder
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.settings.AppSettingsState
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.system.exitProcess

open class PluginRunner : ApplicationStarter {
    private lateinit var dataset: File
    protected lateinit var saveDir: Path
    protected lateinit var supporter: LanguageSupporter
    protected lateinit var ngramType: String

    private val ngramTypes = listOf("BiDirectional", "OneDirectional")

    protected open val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    protected open val javaSmallTest =
        listOf("libgdx", "hadoop")

    protected open val projectList: List<String>? = listOf("libgdx")

    override fun getCommandName(): String = "modelsEvaluator"

    override fun main(args: Array<out String>) {
        try {
            dataset = File(args[1])
            saveDir = Paths.get(args[2])
            supporter = LanguageSupporter.getInstance(
                when (args[3].lowercase(Locale.getDefault())) {
                    "java" -> JavaLanguage.INSTANCE
                    "python" -> PythonLanguage.INSTANCE
                    "kotlin" -> KotlinLanguage.INSTANCE
                    else -> throw AssertionError("Unknown language")
                }
            )
            ngramType = args[4]
            assert(ngramTypes.contains(ngramType))
            evaluate()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            exitProcess(0)
        }
    }

    open val varNamer: VarNamer
        get() = VarNamer(saveDir, supporter, ngramType)

    private fun evaluate() {
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
                val modelRunner = trainModelRunner(project, timeSpentFile)
                val start = Instant.now()
                if (varNamer.predict(modelRunner, project)) {
                    val evaluationTime = Duration.between(start, Instant.now())

                    FileOutputStream(timeSpentFile, true).bufferedWriter().use {
                        it.write("$evaluationTime")
                    }
                }
            } finally {
                FileOutputStream(timeSpentFile, true).bufferedWriter().use {
                    it.write("\n")
                }
                projectToClose = project
            }
        }
        if (projectToClose != null) {
            ProjectManager.getInstance().closeAndDispose(projectToClose)
        }
    }

    private fun trainModelRunner(
        project: Project,
        timeSpentFile: File
    ): NGramModelRunner {
        val start = Instant.now()
        val settings = AppSettingsState.getInstance()
        settings.maxTrainingTime = 10000
        settings.vocabularyCutOff = 0
        val modelRunner = NGramModelRunner()
        ModelBuilder(project, supporter, null).trainModelRunner(modelRunner)
        val trainingTime = Duration.between(start, Instant.now())
        FileOutputStream(timeSpentFile, true).bufferedWriter().use {
            it.write("${project.name},$trainingTime,")
        }
        return modelRunner
    }
}
