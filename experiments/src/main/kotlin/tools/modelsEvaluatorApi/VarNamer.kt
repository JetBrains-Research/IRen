package tools.modelsEvaluatorApi

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import me.tongfei.progressbar.ProgressBar
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.ngram.PersistentNGramModelRunner
import org.jetbrains.iren.services.ModelManager
import org.jetbrains.iren.storages.Context
import org.jetbrains.iren.storages.VarNamePrediction
import tools.ModelPrediction
import tools.ModelPredictions
import tools.VarNamePredictions
import java.io.File
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.streams.asSequence

open class VarNamer(
    val saveDir: Path,
    private val supporter: LanguageSupporter,
    private val ngramType: String,
) {
    open var runParallel = true
    open val maxNumberOfThreads = 8
    protected lateinit var myModelRunner: NGramModelRunner
    private val persistentModelRunners: List<NGramModelRunner> by lazy { preparePersistentRunners() }
    private val mapper = ObjectMapper()
    lateinit var myStatsFile: File

    open fun preparePersistentRunners(): List<NGramModelRunner> {
        val persistentModelPath = saveDir.resolve("tmp_persistent_model")
        println("Preparing persistent counters...")
        val size = PersistentNGramModelRunner(myModelRunner)
            .save(persistentModelPath, null)
        addText(myStatsFile, "$size,")
        return (0 until if (runParallel) maxNumberOfThreads else 1).map {
            println("Loading ${it + 1}")
            val runner = PersistentNGramModelRunner()
            runner.load(persistentModelPath, null)
            runner
        }
    }

    fun predict(
        modelRunner: NGramModelRunner,
        project: Project,
        statsFile: File,
        predictionsFile: File,
        files: Collection<VirtualFile>? = null,
    ): Boolean {
        myModelRunner = modelRunner
        myStatsFile = statsFile
        val start = Instant.now()
        predictParallel(project, predictionsFile, files ?: collectNotPredictedFiles(predictionsFile, project))
        println("Done in ${Duration.between(start, Instant.now())}")
        return true
    }

    private fun predictParallel(
        project: Project,
        predictionsFile: File,
        files: Collection<VirtualFile>,
    ) {
        val total = files.size
        val psiManager = PsiManager.getInstance(project)
        val progressBar = ProgressBar(project.name, total.toLong())
        println("Number of files to parse: $total")
        if (runParallel) files.withIndex().groupBy { it.index % persistentModelRunners.size }.map { (thread, fs) ->
            thread {
                launchThread(thread, fs.map { it.value }, psiManager, predictionsFile, progressBar)
            }
        }.forEach { it.join() }
        else launchThread(0, files, psiManager, predictionsFile, progressBar)
        progressBar.close()
    }

    private fun launchThread(
        thread: Int,
        fs: Collection<VirtualFile>,
        psiManager: PsiManager,
        predictionsFile: File,
        progressBar: ProgressBar,
    ) {
        println("Launching ${thread + 1}-th thread")
        fs.forEach forEach2@{ file ->
            try {
                val filePath = file.path
                val filePredictions = HashMap<String, Collection<VarNamePredictions>>()

                val psiFile =
                    ReadAction.compute<PsiFile, Exception> { psiManager.findFile(file) } ?: return@forEach2
                val predictions = predictPsiFile(psiFile, thread)
                filePredictions[filePath] = predictions ?: return@forEach2
                synchronized(predictionsFile) {
                    addText(predictionsFile, "${mapper.writeValueAsString(filePredictions)}\n")
                }
            } finally {
                progressBar.step()
            }
        }
    }

    protected fun collectNotPredictedFiles(
        predictionsFile: File,
        project: Project,
    ): Collection<VirtualFile> {
        val predictedFilePaths = predictionsFile.bufferedReader().lines().asSequence()
            .map { line ->
                try {
                    mapper.readValue(line, Map::class.java).keys.first()
                } catch (e: JsonProcessingException) {
                    null
                }
            }.filterNotNull()
            .toHashSet()
        return FileTypeIndex.getFiles(
            supporter.fileType,
            GlobalSearchScope.projectScope(project)
        ).filter { file -> file.path !in predictedFilePaths }
    }

    protected open fun predictPsiFile(file: PsiFile, thread: Int): Collection<VarNamePredictions>? {
        return try {
            val variables = ReadAction.compute<List<PsiElement?>, Exception> {
                SyntaxTraverser.psiTraverser()
                    .withRoot(file)
                    .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                    .filter(supporter::isVariableDeclaration)
                    .toList()
            }
            variables.asSequence()
                .filterNotNull()
                .map { v -> predictVarName(v as PsiNameIdentifierOwner, thread) }
                .filterNotNull()
                .toList()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun predictVarName(
        variable: PsiNameIdentifierOwner,
        thread: Int,
    ): VarNamePredictions? {
        val nameIdentifier = variable.nameIdentifier
        if (nameIdentifier === null || nameIdentifier.text == "") return null

        val nGramPredictions = predictWithNGram(variable, thread)
        val nnPredictions = predictWithNN(variable, thread)

        return VarNamePredictions(
            nameIdentifier.text,
            nGramPredictions,
            nnPredictions,
            variable.javaClass.interfaces[0].simpleName,
            ReadAction.compute<Boolean, Exception> {
                LanguageRefactoringSupport.INSTANCE.forContext(variable)
                    ?.isInplaceRenameAvailable(variable, null) ?: false
            }
        )
    }

    open fun predictWithNGram(variable: PsiNameIdentifierOwner, thread: Int): ModelPredictions {
        val startTime = System.nanoTime()
        val runner = prepareThreadRunner(thread, variable)
        val nameSuggestions =
            ReadAction.compute<List<VarNamePrediction>, Exception> { runner.suggestNames(variable, false) }
        val contextStatistics =
            ReadAction.compute<Context.Statistics, Exception> { runner.getContextStatistics(variable, false) }
        val gtProbability = ReadAction.compute<Double, Exception> { runner.getProbability(variable, false).first }
        val time = (System.nanoTime() - startTime) / 1.0e9
        return NGramPredictions(
            nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) },
            time,
            contextStatistics.usageNumber,
            contextStatistics.countsSum,
            gtProbability
        )
    }

    open fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val startTime = System.nanoTime()
        val nameSuggestions: List<VarNamePrediction>
        var contextStatistics = Context.Statistics.EMPTY
        var gtProbability = 0.0
        if (!runParallel) {
//            Predict with RAM version model
            ModelManager.getInstance().forgetFileIfNeeded(myModelRunner, variable.containingFile)
            nameSuggestions = myModelRunner.suggestNames(variable)
            contextStatistics = myModelRunner.getContextStatistics(variable, false)
            gtProbability = myModelRunner.getProbability(variable, false).first
        } else if (ngramType != "BiDirectional") {
            nameSuggestions = listOf()
        } else {
//        Predict with the forward model of the BiDirectional model
            val runner = prepareThreadRunner(thread, variable)
            val forwardModel = NGramModelRunner(
                (runner.model as BiDirectionalModel).forward,
                runner.vocabulary,
                runner.rememberedIdentifiers,
                false,
                runner.order
            )
            nameSuggestions =
                ReadAction.compute<List<VarNamePrediction>, Exception> { forwardModel.suggestNames(variable) }
            contextStatistics =
                ReadAction.compute<Context.Statistics, Exception> { forwardModel.getContextStatistics(variable, false) }
            gtProbability = ReadAction.compute<Double, Exception> { forwardModel.getProbability(variable, false).first }
        }
        val time = (System.nanoTime() - startTime) / 1.0e9
        return NGramPredictions(
            nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) },
            time,
            contextStatistics.usageNumber,
            contextStatistics.countsSum,
            gtProbability
        )
    }

    protected open fun prepareThreadRunner(
        thread: Int,
        variable: PsiNameIdentifierOwner,
    ): NGramModelRunner {
        val runner = persistentModelRunners[thread]
        ReadAction.run<Exception> { ModelManager.getInstance().forgetFileIfNeeded(runner, variable.containingFile) }
        return runner
    }
}

class NGramPredictions(
    predictions: Any,
    time: Double,
    val usageNumber: Int,
    val countsSum: Int,
    val gtProbability: Double = 0.0,
) :
    ModelPredictions(predictions, time)