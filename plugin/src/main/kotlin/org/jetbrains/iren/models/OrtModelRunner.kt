package org.jetbrains.iren.models

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.iren.DOBFContextParser
import org.jetbrains.iren.DOBFModelRunner
import org.jetbrains.iren.config.ModelType
import org.jetbrains.iren.contributors.DOBFContributor.Companion.MODEL_PRIORITY
import org.jetbrains.iren.search.BeamSearchGenerator
import org.jetbrains.iren.storages.VarNamePrediction
import org.jetbrains.iren.storages.Vocabulary
import java.nio.file.Path
import java.util.concurrent.ExecutionException
import kotlin.math.exp

class OrtModelRunner(modelDir: Path, maxSequenceLength: Int = 512, cacheSize: Long = 1024L) : DOBFModelRunner {
    private val contextParser = DOBFContextParser(modelDir, maxSequenceLength)
    val vocabulary = contextParser.vocab
    val model = OrtModel(modelDir.resolve("encoder.quant.onnx"), modelDir.resolve("decoder.quant.onnx"))
    private val cache = CacheBuilder.newBuilder().maximumSize(cacheSize)
        .build(object : CacheLoader<SmartPsiElementPointer<PsiNameIdentifierOwner>, List<VarNamePrediction>>() {
            override fun load(variable: SmartPsiElementPointer<PsiNameIdentifierOwner>) = loadCache(variable)
        })

    private fun loadCache(variable: SmartPsiElementPointer<PsiNameIdentifierOwner>): List<VarNamePrediction> {
        val element = ReadAction.compute<PsiNameIdentifierOwner, Throwable> { variable.element } ?: return listOf()
        val truncatedIdxs = contextParser.getContext(element)
        return predictBeamSearch(truncatedIdxs, model, vocabulary).map { (prediction, prob) ->
            toVariableName(prediction)?.let {
                VarNamePrediction(
                    it, prob, ModelType.DOBF, MODEL_PRIORITY
                )
            }
        }.filterNotNull()
    }

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

    companion object {
        @JvmStatic
        fun predictBeamSearch(
            idxs: List<Int>,
            model: OrtModel,
            vocabulary: Vocabulary,
            beamSize: Int = 10,
            maxLength: Int = 10,
            earlyStopping: Boolean = true,
            lengthPenalty: Float = 1f
        ): Map<List<String>, Double> {
            val generator =
                BeamSearchGenerator(model, vocabulary, beamSize, maxLength, earlyStopping, lengthPenalty)
//                BeamSearchGenerator(model, vocabulary, beamSize, maxLength)
            val preds = generator.generate(idxs)
            val res = preds.map { vocabulary.toWords(it.hyp) to exp(it.score) }
            val sum = res.sumOf { (_, prob) -> prob.toDouble() }
            return res.associate { (tokens, prob) -> tokens to prob / sum }
        }

        @JvmStatic
        fun toVariableName(prediction: List<String>): String? {
            val words =
                java.lang.String.join(" ", prediction.subList(2, prediction.size)).replace("@@ ", "").split(" ")
            return if (words.size == 1) words[0] else null
        }
    }
}