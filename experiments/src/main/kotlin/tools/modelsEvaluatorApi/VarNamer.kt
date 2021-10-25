package tools.modelsEvaluatorApi

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.completion.ngram.slp.modeling.mix.BiDirectionalModel
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import me.tongfei.progressbar.ProgressBar
import org.jetbrains.iren.impl.NGramModelRunner
import org.jetbrains.iren.storages.VarNamePrediction
import org.jetbrains.iren.utils.LanguageSupporter
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.concurrent.thread
import kotlin.streams.asSequence

open class VarNamer(
    private val saveDir: Path,
    private val supporter: LanguageSupporter,
    private val ngramType: String
) {
    private var maxNumberOfThreads = 7

    private val modelRunners: ArrayList<NGramModelRunner> = ArrayList(maxNumberOfThreads)

    fun predict(modelRunner: NGramModelRunner, project: Project): Boolean {
        val modelDir = saveDir.resolve("tmp_model")
        val size = modelRunner.save(modelDir, null)
        val numberOfThreads = (4096 / size).toInt().coerceAtLeast(1).coerceAtMost(maxNumberOfThreads)
        println("Number of threads: $numberOfThreads")
        assert(modelRunners.isEmpty())
        (0 until numberOfThreads).map {
            thread {
                println("Preparing ${it + 1}-th runner")
                val runner = NGramModelRunner(true, true, 6)
                runner.load(modelDir, null)
                modelRunners.add(runner)
            }
        }.forEach { it.join() }
        assert(modelRunners.size == numberOfThreads)
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
        val total = files.size
        val start = Instant.now()
        val psiManager = PsiManager.getInstance(project)
        val progressBar = ProgressBar(project.name, total.toLong())
        println("Number of files to parse: $total")
        files.withIndex().groupBy { it.index % numberOfThreads }.map { (thread, fs) ->
            thread {
                println("Launching ${thread + 1}-th thread")
                fs.map { it.value }.forEach forEach2@{ file ->
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
        }.forEach { it.join() }
        val end = Instant.now()
        val timeSpent = Duration.between(start, end)
        System.out.printf(
            "Done in %s\n",
            timeSpent
        )
        modelRunners.clear()
        progressBar.close()
        return true
    }

    protected open fun predictPsiFile(file: PsiFile, thread: Int): Collection<VarNamePredictions>? {
        return ReadAction.compute<Collection<VarNamePredictions>?, Exception> {
            try {
                val variables =
                    SyntaxTraverser.psiTraverser()
                        .withRoot(file)
                        .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                        .filter(supporter::elementIsVariableDeclaration)
                        .toList()
                modelRunners[thread].forgetPsiFile(file)
                return@compute variables.asSequence()
                    .filterNotNull()
                    .map { v -> predictVarName(v as PsiNameIdentifierOwner, thread) }
                    .filterNotNull()
                    .toList()
            } catch (e: Exception) {
                e.printStackTrace()
                return@compute null
            } finally {
                modelRunners[thread].learnPsiFile(file)
            }
        }
    }

    private fun predictVarName(
        variable: PsiNameIdentifierOwner,
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
            variable.javaClass.interfaces[0].simpleName,
            LanguageRefactoringSupport.INSTANCE.forContext(variable)
                ?.isInplaceRenameAvailable(variable, null) ?: false
        )
    }

    private fun predictWithNGram(variable: PsiNameIdentifierOwner, thread: Int): List<ModelPrediction> {
        val nameSuggestions: List<VarNamePrediction> = modelRunners[thread].suggestNames(variable)
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
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
    val psiInterface: String,
    val inplaceRenameAvailable: Boolean
)

class ModelPrediction(val name: Any, val p: Double)