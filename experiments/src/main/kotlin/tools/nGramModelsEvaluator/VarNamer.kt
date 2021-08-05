package tools.nGramModelsEvaluator

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.jetbrains.rd.util.string.printToString
import org.jetbrains.id.names.suggesting.IdNamesSuggestingModelManager
import org.jetbrains.id.names.suggesting.VarNamePrediction
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor
import org.jetbrains.id.names.suggesting.contributors.GlobalVariableNamesContributor
import org.jetbrains.id.names.suggesting.contributors.ProjectVariableNamesContributor
import org.jetbrains.id.names.suggesting.naturalize.GlobalNaturalizeContributor
import org.jetbrains.id.names.suggesting.naturalize.ProjectNaturalizeContributor
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlin.streams.asSequence

class VarNamer {
    companion object {
        private var ngramContributorClass: Class<out VariableNamesContributor>? = null
        private var naturalizeContributorClass: Class<out VariableNamesContributor>? = null

        fun predict(project: Project, dir: Path, ngramContributorType: String) {
            ngramContributorClass = when (ngramContributorType) {
                "global" -> GlobalVariableNamesContributor::class.java
                "project" -> ProjectVariableNamesContributor::class.java
                else -> throw NotImplementedError("ngramContributorType has to be \"global\" or \"project\"!")
            }
            naturalizeContributorClass = when (ngramContributorType) {
                "global" -> GlobalNaturalizeContributor::class.java
                "project" -> ProjectNaturalizeContributor::class.java
                else -> throw NotImplementedError("ngramContributorType has to be \"global\" or \"project\"!")
            }
            val mapper = ObjectMapper()
            val predictionsFile: File = dir.resolve("${project.name}_${ngramContributorType}_predictions.txt").toFile()
            predictionsFile.parentFile.mkdir()
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
            ).filter { file -> file.path !in predictedFilePaths && file.length < 125 * 1024 }
            var progress = 0
            val total = files.size
            val start = Instant.now()
            val psiManager = PsiManager.getInstance(project)
            println("Number of files to parse: $total")
            for (file in files) {
                val psiFile = psiManager.findFile(file)
                if (psiFile === null) continue
                val filePath = file.path
                val filePredictions = HashMap<String, List<NGramPredictions>>()
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
                    val timeLeft = Duration.ofSeconds((timeSpent.toSeconds() * (1 / fraction - 1)).toLong())
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

        private fun predictPsiFile(file: PsiFile): List<NGramPredictions>? {
            val fileEditorManager = FileEditorManager.getInstance(file.project)
            try {
                val editor = fileEditorManager.openTextEditor(
                    OpenFileDescriptor(
                        file.project,
                        file.virtualFile
                    ), true
                )!!
                if (ngramContributorClass == ProjectVariableNamesContributor::class.java) {
                    IdNamesSuggestingModelManager.getInstance()
                        .getModelRunner(ProjectVariableNamesContributor::class.java, file.project)
                        .forgetPsiFile(file)
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
                    IdNamesSuggestingModelManager.getInstance()
                        .getModelRunner(ProjectVariableNamesContributor::class.java, file.project)
                        .learnPsiFile(file)
                }
                fileEditorManager.closeFile(file.virtualFile)
            }
        }

        private fun predictVarName(variable: PsiVariable, editor: Editor): NGramPredictions? {
            val nameIdentifier = variable.nameIdentifier
            if (nameIdentifier === null || nameIdentifier.text == "") return null

            var startTime = System.nanoTime()
            val nGramPredictions = predictWithNGram(variable)
            val nGramEvaluationTime = (System.nanoTime() - startTime) / 1.0e9

            startTime = System.nanoTime()
            val naturalizePredictions = predictWithNaturalize(variable)
            val naturalizeEvaluationTime = (System.nanoTime() - startTime) / 1e9

            return NGramPredictions(
                nameIdentifier.text,
                nGramPredictions,
                nGramEvaluationTime,
                naturalizePredictions,
                naturalizeEvaluationTime,
                getLinePosition(nameIdentifier, editor),
                variable.javaClass.interfaces[0].simpleName
            )
        }

        private fun predictWithNGram(variable: PsiVariable): List<NGramPrediction> {
            val nameSuggestions: List<VarNamePrediction> = ArrayList()
            val contributor = VariableNamesContributor.EP_NAME.findExtension(ngramContributorClass!!)
            contributor!!.contribute(
                variable,
                nameSuggestions,
                false
            )
            return nameSuggestions.map { x: VarNamePrediction -> NGramPrediction(x.name, x.probability) }
        }

        private fun predictWithNaturalize(variable: PsiVariable): List<NaturalizePrediction> {
            val nameSuggestions: List<VarNamePrediction> = ArrayList()
            val contributor = VariableNamesContributor.EP_NAME.findExtension(naturalizeContributorClass!!)
            contributor!!.contribute(
                variable,
                nameSuggestions,
                false
            )
            return nameSuggestions.map { x: VarNamePrediction -> NaturalizePrediction(x.name, x.probability) }
        }

        private fun getLinePosition(identifier: PsiElement, editor: Editor): Int {
            return editor.offsetToLogicalPosition(identifier.textOffset).line
        }
    }
}

class NGramPredictions(
    val groundTruth: String,
    val nGramPrediction: List<NGramPrediction>,
    val nGramEvaluationTime: Double,
    val naturalizePrediction: List<NaturalizePrediction>,
    val naturalizeEvaluationTime: Double,
    val linePosition: Int,
    val psiInterface: String
)

class NGramPrediction(val name: Any, val p: Double)

class NaturalizePrediction(val name: Any, val logit: Double)