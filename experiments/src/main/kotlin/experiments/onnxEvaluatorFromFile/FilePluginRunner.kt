package experiments.onnxEvaluatorFromFile

import com.google.gson.Gson
import experiments.ModelPrediction
import experiments.ModelPredictions
import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.addText
import me.tongfei.progressbar.ProgressBar
import org.jetbrains.iren.EOS_TOKEN
import org.jetbrains.iren.UNK_TOKEN
import org.jetbrains.iren.VAR_TOKEN
import org.jetbrains.iren.bpe.FastBPEAnalyzer
import org.jetbrains.iren.models.OrtModel
import org.jetbrains.iren.models.OrtModelRunner
import org.jetbrains.iren.storages.PersistentVocabulary
import org.jetbrains.iren.utils.DOBFModelUtils
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.*

class FilePluginRunner : PluginRunner() {
    override fun getCommandName(): String = "FileEvaluator"
    override val numProjects = 10
    override val projectList: List<String>? = null
    override val resumeEvaluation: Boolean = true
    lateinit var modelDir: Path
    val useBpe by lazy { !dataset.name.contains("bpe") }
    lateinit var model: OrtModel
    lateinit var bpe: FastBPEAnalyzer
    lateinit var vocab: PersistentVocabulary
    val gson = Gson()

    override fun evaluate() {
        modelDir = Path.of(
            "/home/igor/IdeaProjects/IRen/plugin/build/idea-sandbox/system/DOBF_models",
            DOBFModelUtils().getName(supporter.language)
        )
        model = OrtModel(modelDir.resolve("encoder.quant.onnx"), modelDir.resolve("decoder.quant.onnx"))
        bpe = FastBPEAnalyzer(modelDir.resolve("codes").toFile())
        vocab = PersistentVocabulary.readFromFile(modelDir.resolve("vocab.txt"), unkToken = UNK_TOKEN)
        println("Evaluating models on file dataset...")
        val predictionsFile: File = saveDir.resolve("${dataset.name}.predictions.jsonl").toFile()
        predictionsFile.parentFile.mkdirs()
        val skip = if (predictionsFile.createNewFile()) 0 else predictionsFile.readLines().size
        val dictionaryFile = File(dataset.absolutePath.replace("obfuscated", "dictionary"))
        external@ for ((obf_line, dict) in ProgressBar.wrap(
            FileInputStream(dataset).bufferedReader().readLines()
                .zip(FileInputStream(dictionaryFile).bufferedReader().readLines()).drop(skip), dataset.name
        )) {
            val strings = dict.split(" | ")
            val projectName = strings[0].trim().replace("@@ ", "")
            val dictionary = HashMap<String, String>()
            for (str in strings.drop(1)) {
                val words = str.split(" ")
                if (words.size < 2) continue@external
                dictionary[words[0]] = if (words.size == 2) words[1] else words.drop(1).joinToString(" ")
            }
            val varDictionary = LinkedHashMap<String, String>()
            var line = obf_line.substring(obf_line.indexOf(" | ") + 3)
            for ((mask, token) in dictionary) {
                if (mask.contains("VAR") && token != "self") varDictionary[mask] = token else line =
                    line.replace(" $mask ", " $token ")
            }
            val modelPredictionsList = mutableListOf<Predictions>()
            for (i in (0 until varDictionary.size).shuffled(Random(42))
//                .take((varDictionary.size / 10).coerceAtLeast(3))) {
                .take(1)) {
                var tokens = line
                var savedMask = ""
                var gt = ""
                for ((j, mt) in varDictionary.asSequence().withIndex()) {
                    val (mask, token) = mt
                    if (j == i) {
                        savedMask = mask
                        gt = token.replace("@@ ", "")
                    } else tokens = tokens.replace(" $mask ", " $token ")
                }
                if (gt == "") continue
                val idxs = prepareIndices(tokens, savedMask, useBpe)
                val start = System.nanoTime()
                val predictions =
                    OrtModelRunner.predictBeamSearch(idxs, model, vocab)
                        .map { (prediction, prob) ->
                            OrtModelRunner.toVariableName(prediction)?.let {
                                ModelPrediction(
                                    it, prob
                                )
                            }
                        }.filterNotNull().sortedBy { it.p }.reversed().take(10)
                modelPredictionsList.add(
                    Predictions(
                        gt, ModelPredictions(predictions, (System.nanoTime() - start) / 1.0e9)
                    )
                )
            }
            val filePredictions = HashMap<String, Collection<Predictions>>()
            filePredictions[projectName] = modelPredictionsList
            addText(predictionsFile, "${gson.toJson(filePredictions)}\n")
        }
    }

    private fun prepareIndices(tokens: String, mask: String, useBpe: Boolean): List<Int> {
        val bpeTokens =
            tokens.split(" $mask ").joinToString(" $VAR_TOKEN ", transform = if (useBpe) bpe::applyBpe else null)
        val idxs = getIndices(bpeTokens)
        val truncatedIdxs = truncateIdxs(idxs)
        return truncatedIdxs
    }

    //    Copypasted from DOBFContextParser
    private fun getIndices(bpeTokens: String): List<Int> {
        val tokensList = mutableListOf("</s>")
        tokensList.addAll(bpeTokens.split(" "))
        tokensList.add("</s>")
        return vocab.toIndices(tokensList)
    }

    val maxSequenceLength = 512
    val eosIdx by lazy { vocab.toIndex(EOS_TOKEN) }

    /**
     * Truncate sequence of indices to max sequence length of transformer encoder
     **/
    private fun truncateIdxs(idxs: List<Int>): List<Int> {
        if (idxs.size <= maxSequenceLength) return idxs
        val (minIdx, maxIdx) = getMinMaxIdxsOfVar(idxs)
        val leftOffset = 100
        var (left, right) = if (maxIdx - minIdx > maxSequenceLength - leftOffset * 2) {
            minIdx - leftOffset to minIdx - leftOffset + maxSequenceLength
        } else {
            val meanIdx = (minIdx + maxIdx) / 2
            meanIdx - maxSequenceLength / 2 to meanIdx + maxSequenceLength / 2
        }
        if (left < 0) {
            left = 0
            right = maxSequenceLength
        } else if (right > idxs.size) {
            left = idxs.size - maxSequenceLength
            right = idxs.size
        }
        val result = idxs.subList(left, right).toMutableList()
        result[0] = eosIdx
        result[result.size - 1] = eosIdx
        return result
    }

    private fun getMinMaxIdxsOfVar(idxs: List<Int>): Pair<Int, Int> {
        val varIdx = vocab.toIndex(VAR_TOKEN)
        return idxs.indexOf(varIdx) to idxs.lastIndexOf(varIdx)
    }
}

class Predictions(
    val groundTruth: String, val nnPrediction: ModelPredictions
)
