package experiments.modelsEvaluatorApi

import com.intellij.ide.impl.ProjectUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.ApplicationStarter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.python.PythonLanguage
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.settings.AppSettingsState
import org.jetbrains.iren.training.ModelBuilder
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.random.Random
import kotlin.system.exitProcess

open class PluginRunner : ApplicationStarter {
    open val numProjects = Int.MAX_VALUE
    open val resumeEvaluation: Boolean = true
    protected lateinit var dataset: File
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

    protected open val projectList: List<String>? = null

    @Deprecated("Specify it as `id` for extension definition in a plugin descriptor")
    override val commandName = null

    override fun main(args: List<String>) {
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
            )!!
            ngramType = args.getOrElse(4) { "BiDirectional" }
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

    protected open fun evaluate() {
        println("Evaluating models...")
        if (ngramType == "OneDirectional") {
            NGramModelRunner.DEFAULT_BIDIRECTIONAL = false
        }
        var projectToClose: Project? = null
        val statsFile: File = saveDir.resolve("modelsStats.csv").toFile()
        statsFile.parentFile.mkdirs()
        if (statsFile.createNewFile()) {
            addText(statsFile, "Project,TrainingTime,VocabularySize,ModelSize,EvaluationTime\n")
        }
        val files = (projectList ?: (dataset.list { file, name ->
            file.isDirectory && !name.startsWith(".")
        } ?: return)
            .asSequence()
            .shuffled(Random(42))
            .take(numProjects)
            .toList())
        for (projectDir in files) {
            val projectPath = dataset.resolve(projectDir)

            val predictionsFile: File = saveDir.resolve("${projectDir}_$ngramType.jsonl").toFile()
            predictionsFile.parentFile.mkdirs()
            if (predictionsFile.createNewFile() || resumeEvaluation) {
                println("Opening project $projectDir...")
                val project = ProjectUtil.openOrImport(projectPath.path, projectToClose, true) ?: continue
                addText(statsFile, "${project.name},")
                try {
                    val trainingStart = Instant.now()
                    val modelRunner = trainModelRunner(project)
                    addText(
                        statsFile,
                        "${Duration.between(trainingStart, Instant.now())},${modelRunner.vocabulary.size()},"
                    )
                    val start = Instant.now()
                    if (varNamer.predict(modelRunner, project, statsFile, predictionsFile)) {
                        addText(statsFile, "${Duration.between(start, Instant.now())}")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    addText(statsFile, "\n")
                    projectToClose = project
                }
            }
        }
        if (projectToClose != null) {
            ProjectManager.getInstance().closeAndDispose(projectToClose)
        }
    }

    open fun trainModelRunner(
        project: Project,
    ): NGramModelRunner {
        val settings = AppSettingsState.getInstance()
        settings.maxTrainingTime = 10000
        settings.vocabularyCutOff = 0
        val modelRunner = NGramModelRunner()
        ModelBuilder(project, supporter, null).trainModelRunner(modelRunner)
        return modelRunner
    }
}

fun addText(statsFile: File, text: String) {
    FileOutputStream(statsFile, true).bufferedWriter()
        .use { it.write(text) }
}