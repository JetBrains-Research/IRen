package org.jetbrains.iren.search

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.iren.EOS_TOKEN
import org.jetbrains.iren.VAR_TOKEN
import org.jetbrains.iren.models.EncoderOutput
import org.jetbrains.iren.models.OrtModel
import org.jetbrains.iren.storages.Vocabulary
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.jvm.Throws

internal class BeamSearchGenerator(
    val model: OrtModel,
    val vocab: Vocabulary,
    val beamSize: Int = 10,
    val maxLength: Int = 10,
    val earlyStoppingSize: Int = 20
) {
    private val eosIdx = vocab.toIndex(EOS_TOKEN)
    private val decoderInput = vocab.toIndices(listOf(EOS_TOKEN, VAR_TOKEN))
    private var encoderOutput: EncoderOutput? = null
    private var eachStepLogProbs: List<MutableList<Float>> = listOf(ArrayList())
    private var nextLogProbs: Array<FloatArray>? = null

    private fun initLogProbs(context: List<Int>) {
        encoderOutput = model.encode(listOf(context))

        val logProbs = model.decode(listOf(decoderInput), encoderOutput!!)
        val scoresByBatch = tensorToArrays2D(logProbs)
        nextLogProbs = logSoftmax(scoresByBatch)

        prepareEncoderOutput()
    }

    private fun tensorToArrays2D(logProbs: OnnxTensor): Array<FloatArray> {
        val logProbArray = logProbs.floatBuffer.array()
        val shape = logProbs.info.shape
        return Array(shape[0].toInt()) {
            logProbArray.sliceArray(it * shape[1].toInt() until (it + 1) * shape[1].toInt())
        }
    }

    private fun prepareEncoderOutput() {
        encoderOutput =
            EncoderOutput(encoderOutput!!.logProbs.repeat(beamSize), encoderOutput!!.lengths.repeat(beamSize))
    }

    /**
     * Repeat tensor on first dimension [n] times. Works with long and float values.
     **/
    private fun OnnxTensor.repeat(n: Int): OnnxTensor {
        val shape = this.info.shape
        val newShape = shape.copyOf()
        newShape[0] = newShape[0] * n
        this.floatBuffer?.let { buffer ->
            val data = buffer.array()
            buffer.limit()
            return OnnxTensor.createTensor(
                model.env,
                FloatBuffer.wrap(FloatArray(data.size * n) { data[it.floorMod(data.size)] }), newShape
            )
        }
        val data = this.longBuffer.array()
        return OnnxTensor.createTensor(
            model.env,
            LongBuffer.wrap(LongArray(data.size * n) { data[it.floorMod(data.size)] }),
            newShape
        )
    }

    private fun sortState(sortMask: IntArray) {
        eachStepLogProbs = sortMask.map { ArrayList(eachStepLogProbs[it]) }
    }

    private fun updateState(sortMask: IntArray, newTokensIds: IntArray) {
        sortState(sortMask)

        sortMask.zip(newTokensIds).forEachIndexed { index, (batchInd, tokenInd) ->
            eachStepLogProbs[index].add(nextLogProbs!![batchInd][tokenInd])
        }
    }

    private fun getLogProbs(search: BeamSearch) {
        val logProbs = model.decode(search.hypotheses(), encoderOutput!!)
        val scoresByBatch = tensorToArrays2D(logProbs)
        nextLogProbs = logSoftmax(scoresByBatch)
    }

    private fun resetState() {
        encoderOutput = null
    }

    private fun isEndOfWords(): List<Boolean> {
        val endOfWords = ArrayList<Boolean>()
        val tokensIds = topk2d(nextLogProbs!!, 1, dim = 1)  // both (batch_size * num_beams, 1)
        tokensIds.forEach {
            endOfWords.add(eosIdx == it[0]) // Check if token is eos
        }

        return endOfWords
    }

    private fun currentHypothesis(
        search: Search,
        mask: List<Boolean> = (0 until search.hypotheses().size).map { true }
    ): List<GenerationInfo> {
        return search.hypotheses().filterIndexed { index, _ -> mask[index] }.zip(eachStepLogProbs)
            .map { (hyp, probs) -> GenerationInfo(probs, hyp) }
    }

    val myRepetitionPenalty = 8f

    fun generate(context: List<Int>): List<List<GenerationInfo>> {
        val search = BeamSearch(vocab, beamSize, decoderInput, myRepetitionPenalty)

        initLogProbs(context)
        sortState(IntArray(search.batchSize))

        val result = ArrayList<List<GenerationInfo>>()
        if (isCancelled()) return result
        var candidatesCount = 0
        for (i in 0 until maxLength) {
            if (candidatesCount > earlyStoppingSize) break
            val stepResult = search.step(nextLogProbs!!)
            updateState(stepResult.sortMask, stepResult.newTokens)

            if (i < maxLength - 1) {
                getLogProbs(search)
            }
            val mask = isEndOfWords()
            candidatesCount += mask.count { it }
            result.add(currentHypothesis(search, mask))
            if (isCancelled()) return result
        }

        resetState()
        return result
    }

    private fun isCancelled() : Boolean{
        return try {
            ProgressManager.checkCanceled()
            false
        } catch (_: ProcessCanceledException) {
            true
        }
    }
}
