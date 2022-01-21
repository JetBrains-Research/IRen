package tools.differentProjectSizes

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.settings.AppSettingsState
import org.jetbrains.iren.training.ModelBuilder
import org.jetbrains.iren.training.ProgressBar
import tools.modelsEvaluatorApi.PluginRunner
import tools.modelsEvaluatorApi.addText
import java.io.File
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

open class SizePluginRunner : PluginRunner() {
        val projectSizes = listOf(0.01, 0.05, 0.1, 0.3, 0.5, 0.7, 1.0)
    override fun getCommandName(): String = "predictProjectDiffSizes"

    override fun evaluate() {
        println("Evaluating models...")
        if (ngramType == "OneDirectional") {
            NGramModelRunner.DEFAULT_BIDIRECTIONAL = false
        }
        val statsFile: File = saveDir.resolve("modelsStats.csv").toFile()
        statsFile.parentFile.mkdirs()
        if (statsFile.createNewFile()) {
            addText(statsFile, "Number,Size(%),Size(files),TrainingTime,VocabularySize,ModelSize,EvaluationTime\n")
        }
        val project = ProjectUtil.openOrImport(dataset.toPath()) ?: return
        for (size in projectSizes) {
            evaluateProjectWithSize(project, size, statsFile)
        }
        ProjectManager.getInstance().closeAndDispose(project)
    }

    private fun evaluateProjectWithSize(project: Project, size: Double, statsFile: File) {
        val numIterations = ceil(3 - 2 * size).toLong()
        for (i in 1..numIterations) {
            val predictionsFile: File = saveDir.resolve("${project.name}_${size}_${i}.jsonl").toFile()
            predictionsFile.parentFile.mkdirs()
            if (!predictionsFile.createNewFile()) continue
            try {
                val files = generateFiles(project, size)
                addText(statsFile, "$i,$size,${files.size},")
                val modelRunner = trainModelRunner(project, files, statsFile)
                val start = Instant.now()
                if (varNamer.predict(modelRunner, project, statsFile, predictionsFile, files)) {
                    addText(statsFile, "${Duration.between(start, Instant.now())}")
                }
            } finally {
                addText(statsFile, "\n")
            }
        }
    }

    private fun generateFiles(project: Project, size: Double): Collection<VirtualFile> {
        val allFiles = FileTypeIndex.getFiles(
            supporter.fileType,
            GlobalSearchScope.projectScope(project)
        )
        return allFiles.asSequence().shuffled().take((allFiles.size * size).toInt()).toList()
    }

    private fun trainModelRunner(
        project: Project,
        files: Collection<VirtualFile>,
        statsFile: File,
    ): NGramModelRunner {
        AppSettingsState.getInstance().maxTrainingTime = 10000
        val start = Instant.now()
        val modelRunner = NGramModelRunner()
        modelRunner.train()
        val progressBar = ProgressBar(files.size, null, null)
        ModelBuilder(project, supporter, null)
            .trainNGramModel(modelRunner, files, progressBar, start)
        modelRunner.eval()
        val trainingTime = Duration.between(start, Instant.now())
        addText(statsFile, "$trainingTime,${modelRunner.vocabulary.size()},")
        return modelRunner
    }
}