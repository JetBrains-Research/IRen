package org.jetbrains.iren.search

import org.jetbrains.iren.EOS_TOKEN
import org.jetbrains.iren.storages.Vocabulary
import java.util.*
import kotlin.math.min

internal class BeamSearch(
    vocab: Vocabulary,
    searchSize: Int,
    private val decoderInput: List<Int>,
    repetitionPenalty: Float = 1.0f
) : Search(vocab, searchSize, repetitionPenalty) {
    private val vocabSize = vocab.size()
    private val eosIdx = vocab.toIndex(EOS_TOKEN)
    private var length = 1.0
    override val batchSize: Int
        get() = myScores.size
    var myScores: MutableList<Float> = arrayListOf(0.0f)
        private set

    private var hypotheses: List<MutableList<Int>> = Array(batchSize) { decoderInput.toMutableList() }.toList()
    private var sortMask: IntArray? = null

    override fun step(stepLogProbs: Array<FloatArray>): StepResult {
        modifyScore(stepLogProbs)

        val stepLogProbsLinearSize = stepLogProbs.sumOf { it.size }
        val logProbs = FloatArray(stepLogProbsLinearSize)
        var offset = 0
        for (i in stepLogProbs.indices) {
            val probs = stepLogProbs[i]
            val score = myScores[i]
            for (value in probs) {
                val currentVal = value + score
                logProbs[offset++] = currentVal
            }
        }

        val topkResult = topk1d(logProbs, searchSize)
        var samples = topkResult.second
        val sampleScores = topkResult.first

        val stepSortMask = IntArray(samples.size) { samples[it].floorDiv(vocabSize) }
        samples = IntArray(samples.size) { samples[it].floorMod(vocabSize) }

        initSortMask()
        updateState(samples, sampleScores, stepSortMask)
        length += 1

        return StepResult(sortMask!!, samples)
    }

    private fun modifyScore(scores: Array<FloatArray>) {
        for (i in hypotheses.indices) {
            val hyp = hypotheses[i]
            if (hyp[hyp.size - 1] == eosIdx) scores[i] = FloatArray(scores[i].size) { Float.NEGATIVE_INFINITY }
            else {
//                TODO: add tokens with different casing ("token" and "Token") to handle repetitions like "fileFile"
                pessimizeScore(scores, i, getTokensWithDiffCasing(hyp).toSet())
            }
        }
    }

    private fun getTokensWithDiffCasing(hyp: MutableList<Int>) = hyp.subList(2, hyp.size).flatMap { tokenIdx ->
        val token = vocab.toWord(tokenIdx)
        val tokens1 = getDiffCasing(token)
        val tokens2 = if (token.endsWith("@@")) tokens1.map { it.replace("@@", "") } else tokens1.map { "$it@@" }
        vocab.toIndices(tokens1 + tokens2)
    }

    private fun getDiffCasing(token: String) = listOf(token,
        token.uppercase(Locale.getDefault()),
        token.lowercase(Locale.getDefault()),
        token.replaceFirstChar { ch -> ch.uppercase() },
        token.replaceFirstChar { ch -> ch.lowercase() }
    )

    private val SPECIAL_TOKENS_START = 4
    private val SPECIAL_TOKENS_END = 514

    private fun pessimizeScore(scores: Array<FloatArray>, ind: Int, uniqueTokens: Set<Int>) {
//        TODO: its better to do it model independent (check in vocabulary all tokens with "<..>" in them, except b/eos)
//        TODO: mb add here check of the new word start (last token doesn't contain "@@")
        for (i in SPECIAL_TOKENS_START until SPECIAL_TOKENS_END) {
            scores[ind][i] = Float.NEGATIVE_INFINITY
        }
        for (previousToken in uniqueTokens) {
            val score = scores[ind][previousToken]
            scores[ind][previousToken] = if (repetitionPenalty == 1f) score else
                score * if (score < 0.0f) repetitionPenalty else 1.0f / repetitionPenalty
        }
    }

    override fun hypotheses(): List<List<Int>> = hypotheses

    override fun lastPredictions(): IntArray {
        require(hypotheses.isNotEmpty() && hypotheses[0].size > 0) { "Can't get last predictions if no steps have been performed" }
        return IntArray(hypotheses.size) { hypotheses[it].last() }
    }

    override fun scores(): List<Float> = myScores

    private fun initSortMask() {
        sortMask = IntArray(batchSize) { it }
    }

    private fun updateState(samples: IntArray, sampleScores: FloatArray, sortMask: IntArray) {
        sortState(sortMask)

        myScores = sampleScores.toMutableList()
        for (i in hypotheses.indices) {
            hypotheses[i].add(samples[i])
        }
    }

    private fun sortState(sortMask: IntArray? = null) {
        if (sortMask == null) {
            applySliceToState(topk1d(myScores.toFloatArray(), min(searchSize, myScores.size)).second)
        } else {
            applySliceToState(sortMask)
        }
    }

    private fun applySliceToState(tensorSlice: IntArray) {
        myScores = myScores.slice(tensorSlice).toMutableList()
        hypotheses = tensorSlice.map { ArrayList(hypotheses[it]) }
        if (sortMask != null) {
            sortMask = sortMask!!.sliceArray(tensorSlice)
        }
    }
}
