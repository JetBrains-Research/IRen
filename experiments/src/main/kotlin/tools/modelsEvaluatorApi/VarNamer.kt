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
import org.jetbrains.iren.api.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.ngram.PersistentNGramModelRunner
import org.jetbrains.iren.services.ModelManager
import org.jetbrains.iren.storages.VarNamePrediction
import tools.ModelPrediction
import tools.VarNamePredictions
import java.io.File
import java.io.FileOutputStream
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
    var maxNumberOfThreads = 8
    protected lateinit var myModelRunner: NGramModelRunner
    private val persistentModelRunners: List<NGramModelRunner> by lazy { preparePersistentRunners() }
    private val mapper = ObjectMapper()

    private fun preparePersistentRunners(): List<NGramModelRunner> {
        val persistentModelPath = saveDir.resolve("tmp_persistent_model")
        println("Preparing persistent counters...")
        PersistentNGramModelRunner(myModelRunner).save(persistentModelPath, null)
        return (0 until if (runParallel) maxNumberOfThreads else 1).map {
            println("Loading ${it + 1}")
            val runner = PersistentNGramModelRunner()
            runner.load(persistentModelPath, null)
            runner
        }
    }

    fun predict(modelRunner: NGramModelRunner, project: Project): Boolean {
        myModelRunner = modelRunner
        val predictionsFile: File = saveDir.resolve("${project.name}_${ngramType}_predictions.jsonl").toFile()
        predictionsFile.parentFile.mkdirs()
        predictionsFile.createNewFile()
        val files = collectNotPredictedFiles(predictionsFile, project)
        if (files.isEmpty()) return false
        val start = Instant.now()
        predictParallel(files, project, predictionsFile)
        val end = Instant.now()
        val timeSpent = Duration.between(start, end)
        System.out.printf(
            "Done in %s\n",
            timeSpent
        )
        return true
    }

    private fun predictParallel(
        files: List<VirtualFile>,
        project: Project,
        predictionsFile: File,
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
        fs: List<VirtualFile>,
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
                    FileOutputStream(predictionsFile, true)
                        .bufferedWriter().use {
                            it.write(mapper.writeValueAsString(filePredictions))
                            it.newLine()
                        }
                }
            } finally {
                progressBar.step()
            }
        }
    }

    private fun collectNotPredictedFiles(
        predictionsFile: File,
        project: Project,
    ): List<VirtualFile> {
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
                    .filter(supporter::elementIsVariableDeclaration)
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

        var startTime = System.nanoTime()
        val nGramPredictions = predictWithNGram(variable, thread)
        val nGramEvaluationTime = (System.nanoTime() - startTime) / 1.0e9

        startTime = System.nanoTime()
        val nnPredictions = predictWithNN(variable, thread)
        val nnEvaluationTime = (System.nanoTime() - startTime) / 1.0e9

        return VarNamePredictions(
            nameIdentifier.text,
            nGramPredictions,
            nGramEvaluationTime,
            nnPredictions,
            nnEvaluationTime,
            variable.javaClass.interfaces[0].simpleName,
            ReadAction.compute<Boolean, Exception> {
                LanguageRefactoringSupport.INSTANCE.forContext(variable)
                    ?.isInplaceRenameAvailable(variable, null) ?: false
            }
        )
    }

    private fun predictWithNGram(variable: PsiNameIdentifierOwner, thread: Int): List<ModelPrediction> {
        val runner = prepareThreadRunner(thread, variable)
        val nameSuggestions = ReadAction.compute<List<VarNamePrediction>, Exception> { runner.suggestNames(variable) }
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }

    open fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
//        Predict with the forward model of the BiDirectional model
        assert(ngramType == "BiDirectional")
        val runner = prepareThreadRunner(thread, variable)
        val forwardModel = NGramModelRunner(
            (runner.model as BiDirectionalModel).forward,
            runner.vocabulary,
            runner.rememberedIdentifiers,
            false,
            runner.order
        )
        val nameSuggestions =
            ReadAction.compute<List<VarNamePrediction>, Exception> { forwardModel.suggestNames(variable) }
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }

    private fun prepareThreadRunner(
        thread: Int,
        variable: PsiNameIdentifierOwner,
    ): NGramModelRunner {
        val runner = persistentModelRunners[thread]
        ReadAction.run<Exception> { ModelManager.getInstance().forgetFileIfNeeded(runner, variable.containingFile) }
        return runner
    }
}