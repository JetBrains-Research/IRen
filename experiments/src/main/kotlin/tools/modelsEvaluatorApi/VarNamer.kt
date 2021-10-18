package tools.modelsEvaluatorApi

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.*
import org.jetbrains.iren.impl.NGramModelRunner
import org.jetbrains.iren.storages.VarNamePrediction
import org.jetbrains.iren.utils.LanguageSupporter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.streams.asSequence

open class VarNamer(
    private val saveDir: Path,
    private val supporter: LanguageSupporter,
    private val ngramType: String
) {
    private var maxNumberOfThreads = 6

    private val modelRunners: ArrayList<NGramModelRunner> = ArrayList(maxNumberOfThreads)
    private lateinit var fileEditorManager: FileEditorManager

    fun predict(modelRunner: NGramModelRunner, project: Project): Boolean {
        val modelDir = saveDir.resolve("tmp_model")
        val size = modelRunner.save(modelDir, null)
        val numberOfThreads = (4096 / size).toInt().coerceAtLeast(1).coerceAtMost(maxNumberOfThreads)
        println("Number of threads: $numberOfThreads")
        assert(modelRunners.isEmpty())
        for (i in 0 until numberOfThreads) {
            println("Preparing ${i + 1}-th runner")
            val runner = NGramModelRunner(true, true, 6)
            runner.load(modelDir, null)
            modelRunners.add(runner)
        }
        assert(modelRunners.size == numberOfThreads)
        fileEditorManager = FileEditorManager.getInstance(project)
        val mapper = ObjectMapper()
        val predictionsFile: File = saveDir.resolve("${project.name}_${ngramType}_predictions.jsonl").toFile()
        predictionsFile.parentFile.mkdirs()
        predictionsFile.createNewFile()
        val predictedFilePaths = predictionsFile.bufferedReader().lines().asSequence()
            .map { line ->
                try {
                    mapper.readValue(line, Map::class.java).keys.first()
                } catch (e: JsonProcessingException) {
                    null
                }
            }.filterNotNull()
            .toHashSet()
        val files = FileTypeIndex.getFiles(
            supporter.fileType,
            GlobalSearchScope.projectScope(project)
        ).filter { file -> file.path !in predictedFilePaths }
        if (files.isEmpty()) return false
        var progress = 0
        val total = files.size
        val start = Instant.now()
        val psiManager = PsiManager.getInstance(project)
        println("Number of files to parse: $total")
        runBlocking {
            coroutineScope {
                files.withIndex().groupBy { it.index % numberOfThreads }.forEach { (thread, fs) ->
                    launch {
                        println("Launching ${thread + 1}-th thread")
                        fs.map { it.value }.forEach forEach2@{ file ->
                            val psiFile = psiManager.findFile(file)
                            if (psiFile === null) return@forEach2
                            val filePath = file.path
                            val filePredictions = HashMap<String, Collection<VarNamePredictions>>()

                            val predictions = predictPsiFile(psiFile, thread)

                            withContext(Dispatchers.Default) {
                                val fraction = ++progress / total.toDouble()
                                if (total < 100 || progress % (total / 100) == 0) {
                                    val timeSpent = Duration.between(start, Instant.now())
                                    val timeLeft = Duration.ofSeconds((timeSpent.seconds * (1 / fraction - 1)).toLong())
                                    System.out.printf(
                                        "Status: %.0f%%;\tTime spent: %s;\tTime left: %s\r",
                                        fraction * 100.0,
                                        timeSpent,
                                        timeLeft
                                    )
                                }
                            }
                            filePredictions[filePath] = predictions ?: return@forEach2
                            withContext(Dispatchers.IO) {
                                FileOutputStream(predictionsFile, true)
                                    .bufferedWriter().use {
                                        it.write(mapper.writeValueAsString(filePredictions))
                                        it.newLine()
                                    }
                            }
                        }
                    }
                } // forEach
            }
        }
        val end = Instant.now()
        val timeSpent = Duration.between(start, end)
        System.out.printf(
            "Done in %s\n",
            timeSpent
        )
        modelRunners.clear()
        return true
    }

    protected open suspend fun predictPsiFile(file: PsiFile, thread: Int): Collection<VarNamePredictions>? {
        try {
            val variables =
                SyntaxTraverser.psiTraverser()
                    .withRoot(file)
                    .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                    .filter { element: PsiElement? ->
                        element is PsiNameIdentifierOwner &&
                                supporter.isVariableDeclaration(element)
                    }
                    .toList()
            val editor = fileEditorManager.openTextEditor(
                OpenFileDescriptor(
                    file.project,
                    file.virtualFile
                ), true
            )!!
            modelRunners[thread].forgetPsiFile(file)
            val result = mutableListOf<VarNamePredictions?>()
            coroutineScope {
                variables
                    .asSequence()
                    .filterNotNull()
                    .map { v -> async { predictVarName(v as PsiNameIdentifierOwner, editor, thread) } }
                    .forEach { result.add(it.await()) }
            }
            return result.filterNotNull()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            modelRunners[thread].learnPsiFile(file)
            fileEditorManager.closeFile(file.virtualFile)
        }
    }

    private fun predictVarName(
        variable: PsiNameIdentifierOwner,
        editor: Editor,
        thread: Int
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
            getLinePosition(nameIdentifier, editor),
            variable.javaClass.interfaces[0].simpleName,
            LanguageRefactoringSupport.INSTANCE.forContext(variable)
                ?.isInplaceRenameAvailable(variable, null) ?: false
        )
    }

    private fun predictWithNGram(variable: PsiNameIdentifierOwner, thread: Int): List<ModelPrediction> {
        val nameSuggestions: List<VarNamePrediction> = modelRunners[thread].suggestNames(variable)
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }

    private fun getLinePosition(identifier: PsiElement, editor: Editor): Int {
        return editor.offsetToLogicalPosition(identifier.textOffset).line
    }

    open fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
//        Predict with the forward model of the BiDirectional model
        assert(ngramType == "BiDirectional")
        val forwardModel = NGramModelRunner(
            (modelRunners[thread].model as BiDirectionalModel).forward,
            modelRunners[thread].vocabulary,
            modelRunners[thread].rememberedIdentifiers,
            false,
            modelRunners[thread].order
        )
        val nameSuggestions: List<VarNamePrediction> = forwardModel.suggestNames(variable)
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }
}

class VarNamePredictions(
    val groundTruth: String,
    val nGramPrediction: List<ModelPrediction>,
    val nGramEvaluationTime: Double,
    val nnPrediction: Any,
    val nnResponseTime: Double,
    val linePosition: Int,
    val psiInterface: String,
    val inplaceRenameAvailable: Boolean
)

class ModelPrediction(val name: Any, val p: Double)