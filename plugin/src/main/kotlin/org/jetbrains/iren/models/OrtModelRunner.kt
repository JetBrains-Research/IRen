package org.jetbrains.iren.models

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.iren.DOBFContextParser
import org.jetbrains.iren.DOBFModelRunner
import org.jetbrains.iren.config.ModelType
import org.jetbrains.iren.contributors.DOBFContributor.Companion.MODEL_PRIORITY
import org.jetbrains.iren.search.BeamSearchGenerator
import org.jetbrains.iren.search.logSoftmax
import org.jetbrains.iren.storages.Context
import org.jetbrains.iren.storages.VarNamePrediction
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.math.exp

const val CACHE_SIZE = 1024L

class OrtModelRunner(modelDir: Path) : DOBFModelRunner {
    val contextParser = DOBFContextParser(modelDir)
    val vocabulary = contextParser.vocab
    val model = OrtModel(modelDir.resolve("encoder.quant.onnx"), modelDir.resolve("decoder.quant.onnx"))
    private val cache = CacheBuilder.newBuilder()
        .maximumSize(CACHE_SIZE)
        .build(object : CacheLoader<SmartPsiElementPointer<PsiNameIdentifierOwner>, List<VarNamePrediction>>() {
            override fun load(variable: SmartPsiElementPointer<PsiNameIdentifierOwner>): List<VarNamePrediction> {
                val truncatedIdxs =
                    contextParser.getContext(ReadAction.compute<PsiNameIdentifierOwner, Throwable> { variable.element }
                        ?: return listOf())
                return predictBeamSearch(truncatedIdxs).map { (prediction, prob) ->
                    toVariableName(prediction)?.let {
                        VarNamePrediction(
//                    join(" ", vocab.toWords(prediction)).replace("@@ ", ""),
                            it, prob, ModelType.DOBF, MODEL_PRIORITY
                        )
                    }
                }.filterNotNull()
            }
        })

    override fun predict(variable: PsiNameIdentifierOwner): List<VarNamePrediction> {
        return try {
//            Use smart pointers here because after renaming PsiElement is replaced with the new one and caching doesn't work.
            cache.get(ReadAction.compute<SmartPsiElementPointer<PsiNameIdentifierOwner>, Throwable> {
                SmartPointerManager.createPointer(
                    variable
                )
            })
        } catch (_: ExecutionException) {
            return listOf()
        }
    }

    private fun toVariableName(prediction: List<String>): String? {
        val wordList = java.lang.String.join(" ", prediction.subList(2, prediction.size)).replace("@@ ", "").split(" ")
        return if (wordList.size == 1) wordList[0] else null
    }

    fun predictGreedy(idxs: List<Int>): Map<List<String>, Double> {
        val encoderOutput = model.encode(listOf(idxs))

        val eosIdx = contextParser.eosIdx
        val toDecList = mutableListOf(eosIdx)

        var logProb = .0
        for (i in 0..5) {
            val out = model.decode(listOf(toDecList), encoderOutput)
            val idx = out.floatBuffer.array().withIndex().maxByOrNull { it.value }?.index!!
            if (idx == eosIdx) break
            logProb += logSoftmax(arrayOf(out.floatBuffer.array()))[0][idx]
            toDecList.add(idx)  // T += 1
        }
        return mapOf(vocabulary.toWords(toDecList) to logProb)
    }

    fun predictBeamSearch(idxs: List<Int>): Map<List<String>, Double> {
        val generator = BeamSearchGenerator(model, vocabulary)
        val preds = generator.generate(idxs)
        val res =
            preds.flatMap { it.map { pred -> vocabulary.toWords(pred.ids.toList()) to exp(pred.logProbs.sum()).toDouble() } }
        val sum = res.sumOf { (_, prob) -> prob }
        return res.associate { (tokens, prob) -> tokens to prob / sum }
    }
}