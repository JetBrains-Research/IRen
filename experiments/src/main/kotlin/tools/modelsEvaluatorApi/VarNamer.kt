package tools.modelsEvaluatorApi

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.rd.util.string.printToString
import org.jetbrains.iren.ModelManager
import org.jetbrains.iren.api.VariableNamesContributor
import org.jetbrains.iren.contributors.NGramVariableNamesContributor
import org.jetbrains.iren.contributors.ProjectVariableNamesContributor
import org.jetbrains.iren.storages.VarNamePrediction
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.streams.asSequence

abstract class VarNamer {
    private var ngramContributorClass: Class<out NGramVariableNamesContributor>? = null

    fun predict(project: Project, dir: Path, ngramContributorType: String) {
        ngramContributorClass = when (ngramContributorType) {
//            "global" -> GlobalVariableNamesContributor::class.java
            "project" -> ProjectVariableNamesContributor::class.java
            else -> throw NotImplementedError("ngramContributorType has to be \"global\" or \"project\"!")
        }
        val mapper = ObjectMapper()
        val predictionsFile: File = dir.resolve("${project.name}_${ngramContributorType}_predictions.txt").toFile()
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
            JavaFileType.INSTANCE,
            GlobalSearchScope.projectScope(project)
        ).filter { file -> file.path !in predictedFilePaths }
        var progress = 0
        val total = files.size
        val start = Instant.now()
        val psiManager = PsiManager.getInstance(project)
        println("Number of files to parse: $total")
        for (file in files) {
            val psiFile = psiManager.findFile(file)
            if (psiFile === null) continue
            val filePath = file.path
            val filePredictions = HashMap<String, List<VarNamePredictions>>()
            val preds = predictPsiFile(psiFile)
            if (preds === null) continue
            filePredictions[filePath] = preds
            FileOutputStream(predictionsFile, true).bufferedWriter().use {
                it.write(mapper.writeValueAsString(filePredictions))
                it.newLine()
            }
            val fraction = ++progress / total.toDouble()
            if (total < 100 || progress % (total / 100) == 0) {
                val timeSpent = Duration.between(start, Instant.now())
                val timeLeft = Duration.ofSeconds((timeSpent.seconds * (1 / fraction - 1)).toLong())
                System.out.printf(
                    "Status: %.0f%%;\tTime spent: %s;\tTime left: %s\r",
                    fraction * 100.0,
                    timeSpent.printToString(),
                    timeLeft.printToString()
                )
            }
        }
        val end = Instant.now()
        val timeSpent = Duration.between(start, end)
        System.out.printf(
            "Done in %s\n",
            timeSpent.printToString()
        )
    }

    protected open fun predictPsiFile(file: PsiFile): List<VarNamePredictions>? {
        val fileEditorManager = FileEditorManager.getInstance(file.project)
        try {
            val editor = fileEditorManager.openTextEditor(
                OpenFileDescriptor(
                    file.project,
                    file.virtualFile
                ), true
            )!!
            if (ngramContributorClass == ProjectVariableNamesContributor::class.java) {
                ModelManager.getInstance()
                    .getModelRunner(ModelManager.getName(file.project, JavaLanguage.INSTANCE))
                    ?.forgetPsiFile(file)
            }
            val predictionsList = SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(TextRange(0, 64 * 1024)) // first 128 KB of chars
                .filter { element: PsiElement? -> element is PsiVariable }
                .toList()
                .asSequence()
                .filterNotNull()
                .map { e -> e as PsiVariable }
                .map { v -> predictVarName(v, editor) }
                .filterNotNull()
                .toList()
            return predictionsList
        } catch (e: Exception) {
            return null
        } finally {
            if (ngramContributorClass == ProjectVariableNamesContributor::class.java) {
                ModelManager.getInstance()
                    .getModelRunner(ModelManager.getName(file.project, JavaLanguage.INSTANCE))
                    ?.learnPsiFile(file)
            }
            fileEditorManager.closeFile(file.virtualFile)
        }
    }

    private fun predictVarName(variable: PsiVariable, editor: Editor): VarNamePredictions? {
        val nameIdentifier = variable.nameIdentifier
        if (nameIdentifier === null || nameIdentifier.text == "") return null

        var startTime = System.nanoTime()
        val nGramPredictions = predictWithNGram(variable)
        val nGramEvaluationTime = (System.nanoTime() - startTime) / 1.0e9

        startTime = System.nanoTime()
        val nnPredictions = predictWithNN(variable)
        val nnEvaluationTime = (System.nanoTime() - startTime) / 1e9

        return VarNamePredictions(
            nameIdentifier.text,
            nGramPredictions,
            nGramEvaluationTime,
            nnPredictions,
            nnEvaluationTime,
            getLinePosition(nameIdentifier, editor),
            variable.javaClass.interfaces[0].simpleName
        )
    }

    private fun predictWithNGram(variable: PsiVariable): List<ModelPrediction> {
        val nameSuggestions: List<VarNamePrediction> = ArrayList()
        val contributor = VariableNamesContributor.EP_NAME.findExtension(ngramContributorClass!!)
        contributor!!.contribute(
            variable,
            nameSuggestions
        )
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }

    private fun getLinePosition(identifier: PsiElement, editor: Editor): Int {
        return editor.offsetToLogicalPosition(identifier.textOffset).line
    }

    abstract fun predictWithNN(variable: PsiVariable): Any
}

class VarNamePredictions(
    val groundTruth: String,
    val nGramPrediction: List<ModelPrediction>,
    val nGramEvaluationTime: Double,
    val nnPrediction: Any,
    val nnResponseTime: Double,
    val linePosition: Int,
    val psiInterface: String
)

class ModelPrediction(val name: Any, val p: Double)