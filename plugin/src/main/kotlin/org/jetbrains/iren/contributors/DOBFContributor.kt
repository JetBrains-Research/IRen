package org.jetbrains.iren.contributors

import com.intellij.psi.PsiNameIdentifierOwner
import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.model.Model
import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.LongNDArray
import io.kinference.ndarray.arrays.MutableFloatNDArray
import io.kinference.ndarray.arrays.NumberNDArray
import io.kinference.ndarray.arrays.tiled.LongTiledArray
import io.kinference.ndarray.toLongArray
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.VariableNamesContributor
import org.jetbrains.iren.bpe.FastBPEAnalyzer
import org.jetbrains.iren.storages.PersistentVocabulary
import org.jetbrains.iren.storages.VarNamePrediction
import java.lang.String.join
import kotlin.io.path.Path
import kotlin.math.exp
import kotlin.math.ln

class DOBFContributor : VariableNamesContributor {
    //    TODO: load model/bpe on startup
    private val modelDir = Path("/home/igor/PycharmProjects/CodeGen/training_artifacts/onnx_models_old/")
    private val bpeDir = Path("/home/igor/PycharmProjects/CodeGen/data/bpe/cpp-java-python")
    private val VAR_TOKEN = "VAR_10"

    override fun contribute(variable: PsiNameIdentifierOwner, predictionList: MutableList<VarNamePrediction>): Int {
        val context = (LanguageSupporter.getInstance(variable.language)
            ?.getContext(variable, true, false) ?: return 0).with(VAR_TOKEN)

//        TODO: extract it to another class
//        Apply BPE to context
        val bpe = FastBPEAnalyzer(bpeDir.resolve("codes").toFile())
        val toBpe = ArrayList<String>()
        val tokensLists = context.splitByUsages()
        for (tokensList in tokensLists) {
            toBpe.add(bpe.applyBpe(join(" ", tokensList)))
        }
        val bpeTokens = join(" $VAR_TOKEN ", toBpe)

//        TODO: separate code below to different classes
//        String tokens to vocab indices
        val vocab = PersistentVocabulary.readFromPath(modelDir.resolve("vocab.txt"))
        val tokensList = mutableListOf("</s>")
        tokensList.addAll(bpeTokens.split(" "))
        tokensList.add("</s>")
        val idxs = vocab.toIndices(tokensList).toIntArray()

//        Encoder's inference part
//        B=1 - batch size, S - source length, T - target length, E=256 - embeddings size, V=64000 - vocabulary size
//        All shapes from debugging
        val longIds = LongTiledArray(arrayOf(idxs.toLongArray()))
        var inputIds: NumberNDArray = LongNDArray(longIds, Strides(intArrayOf(1, idxs.size)))
        inputIds = inputIds.transpose(intArrayOf(1, 0))
        val inputLengths = LongNDArray(intArrayOf(1)) { idxs.size.toLong() }

        val input = ArrayList<KITensor>()
        input.add(inputIds.asTensor("x"))  // Shape: [S, B]
        input.add(inputLengths.asTensor("lengths"))  // Shape: [B]

        val encoder = Model.load(modelDir.resolve("encoder.opt.onnx").toFile().readBytes(), KIEngine)
        val enc1 = (encoder.predict(input)["output"] as KITensor).data  // Shape: [B, S, E]

//        Decoder's inference part
        val decoder = Model.load(modelDir.resolve("decoder.opt.onnx").toFile().readBytes(), KIEngine)

        val eosIdx = vocab.toIndex("</s>")
        val toDecList = mutableListOf(eosIdx)  // Size: T
        var toDec = LongTiledArray(arrayOf(toDecList.toIntArray().toLongArray()))
        var decIds: NumberNDArray = LongNDArray(toDec, Strides(intArrayOf(toDec.size, 1)))  // Shape: [T, B]
        var decLengths = LongNDArray(intArrayOf(1)) { toDec.size.toLong() }  // Shape: [B]

        val decInput = ArrayList<KITensor>()
//        ['x', 'lengths', 'src_enc', 'src_len']
        decInput.add(decIds.asTensor("x"))  // Shape: [T, B]
        decInput.add(decLengths.asTensor("lengths"))  // Shape: [B]
        decInput.add(enc1.asTensor("src_enc"))  // Shape: [B, S, E]
        decInput.add(inputLengths.asTensor("src_len"))  // Shape: [B]

//        Problem: it keeps generating VAR_i token
        var logProb = .0
        for (i in 0..5) {
            val out = ((decoder.predict(decInput)["output"] as KITensor).data as FloatNDArray)  // Shape: [B, V]
            val idx: Int = out.argmax(1).array[0]
            if (idx == eosIdx) break
            logProb += logSoftmax(arrayOf((out.row(0) as MutableFloatNDArray).array.toArray()))[0][idx]
            toDecList.add(idx)  // T += 1
            toDec = LongTiledArray(arrayOf(toDecList.toIntArray().toLongArray()))
            decIds = LongNDArray(toDec, Strides(intArrayOf(toDec.size, 1)))  // Shape: [T, B]
            decLengths = LongNDArray(intArrayOf(1)) { toDec.size.toLong() }  // Shape: [B]

            decInput.clear()
//        ['x', 'lengths', 'src_enc', 'src_len']
            decInput.add(decIds.asTensor("x"))  // Shape: [T, B]
            decInput.add(decLengths.asTensor("lengths"))  // Shape: [B]
            decInput.add(enc1.asTensor("src_enc"))  // Shape: [B, S, E]
            decInput.add(inputLengths.asTensor("src_len"))  // Shape: [B]
        }
        predictionList.add(VarNamePrediction(
//            join(" ", vocab.toWords(toDecList)).replace("@@ ", "").split(" ")[2],
            join(" ", vocab.toWords(toDecList)).replace("@@ ", ""),
            exp(logProb),
            100000
        ))
        return 100000
    }

    override fun getProbability(variable: PsiNameIdentifierOwner): Pair<Double, Int> {
//      get prob of the existing name. For now, it's used for debugging, so forget it.
        return Pair(0.0, 0)
    }
}

internal fun logSoftmax(scores: Array<FloatArray>): Array<FloatArray> {
    val expScores = Array(scores.size) {
        val curScores = scores[it]
        val maxScore = curScores.maxOrNull() ?: .0F
        FloatArray(curScores.size) { i -> exp(curScores[i] - maxScore) }
    }
    for (score in expScores) {
        val scoresSum = score.sum()
        for (i in score.indices) score[i] = ln(score[i] / scoresSum)
    }
    return expScores
}

//Error with quant decoder
//java.lang.IllegalStateException: Cannot broadcast shapes
//	at io.kinference.ndarray.broadcasting.Broadcasting.broadcastShape(Broadcasting.kt:26)
//	at io.kinference.ndarray.arrays.FloatNDArray.expand(FloatNDArray.kt:1193)
//	at io.kinference.ndarray.arrays.FloatNDArray.expand(FloatNDArray.kt:17)
//	at io.kinference.core.operators.tensor.ExpandVer8.apply(Expand.kt:52)
//	at io.kinference.operator.Operator.applyWithCheck(Operator.kt:124)
//	at io.kinference.graph.Graph$execute$2.invoke(Graph.kt:146)
//	at io.kinference.graph.Graph$execute$2.invoke(Graph.kt:145)
//	at io.kinference.profiler.ProfilingContextKt.profile(ProfilingContext.kt:27)
//	at io.kinference.graph.Graph.execute(Graph.kt:145)
//	at io.kinference.core.model.KIModel.predict(KIModel.kt:30)
//	at io.kinference.model.Model$DefaultImpls.predict$default(Model.kt:7)
//	at org.jetbrains.iren.contributors.DOBFContributor.contribute(DOBFContributor.kt:69)