package org.jetbrains.iren.search

import org.jetbrains.iren.EOS_TOKEN
import org.jetbrains.iren.SEP_TOKEN
import org.jetbrains.iren.VAR_TOKEN
import org.jetbrains.iren.models.EncoderOutput
import org.jetbrains.iren.models.OrtModel
import org.jetbrains.iren.storages.Vocabulary
import org.jetbrains.iren.utils.*
import java.util.*

class BeamSearchGenerator(
    val model: OrtModel,
    val vocab: Vocabulary,
    val beamSize: Int = 10,
    val maxLength: Int = 10,
    val earlyStopping: Boolean = true,
    val lengthPenalty: Float = 1.0f,
    val repetitionPenalty: Float = 4.0f
) {
    private val eosIdx = vocab.toIndex(EOS_TOKEN)
    private val sepIdx = vocab.toIndex(SEP_TOKEN)
    private val decoderInput = vocab.toIndices(listOf(EOS_TOKEN, VAR_TOKEN))
    val nWords = vocab.size()

    fun generate(idxs: List<Int>): List<Hypothesis> {
        val encoderOutput = model.encode(listOf(idxs))

        val search = BeamHypothesis(beamSize, maxLength, lengthPenalty, earlyStopping)
        var curLen = 0

        val nextBeam = mutableListOf(decoderInput)
        val beamScores = mutableListOf(.0f)
        var srcEmbeddings = encoderOutput.repeat(nextBeam.size)

        while (curLen < maxLength) {
            if (srcEmbeddings.logProbs.info.shape[0].toInt() != nextBeam.size)
                srcEmbeddings = encoderOutput.repeat(nextBeam.size)
            val (nextScores, nextWords) = generateWordsWithScores(nextBeam, beamScores, srcEmbeddings)
            if (search.isDone(nextScores.maxOf { it })) break
            val nextSentBeam = prepareNextBeam(nextScores, nextWords, curLen, search, nextBeam)
            if (nextSentBeam.size == 0) break // Either next tokens are EOS or SEP, or maxLength is reached
            updateBeam(nextBeam, beamScores, nextSentBeam)
            curLen += 1
            if (isCancelled()) break
        }
        return search.hyps.sortedByDescending { it.score }
    }

    private fun generateWordsWithScores(
        nextBeam: MutableList<List<Int>>,
        beamScores: MutableList<Float>,
        srcEmbeddings: EncoderOutput
    ): Pair<FloatArray, IntArray> {
        val tensor = model.decode(nextBeam, srcEmbeddings)
        val arrays = tensorToArrays2D(tensor)
        //            Comparison with default: 0.07% F1 worse, 7 ms better
        val scores = fastLogSoftmax(arrays)
        val wordsToPessimize = getWordsToPessimize(nextBeam)
        val scoresFlatten = FloatArray(scores.size * nWords) {
            val beamIdx = it / nWords
            val wordIdx = it % nWords
            beamScores[beamIdx] + pessimizeScore(wordIdx, scores[beamIdx][wordIdx], wordsToPessimize[beamIdx])
        }
        return topk1d(scoresFlatten, 2 * beamSize)
    }

    private fun prepareNextBeam(
        nextScores: FloatArray,
        nextWords: IntArray,
        curLen: Int,
        search: BeamHypothesis,
        nextBeam: MutableList<List<Int>>
    ): MutableList<Pair<List<Int>, Float>> {
        val nextSentBeam = mutableListOf<Pair<List<Int>, Float>>()
        for (i in nextScores.indices) {
            val nextWord = nextWords[i]
            val beamIdx = nextWord / nWords
            val wordIdx = nextWord % nWords
            if (wordIdx == eosIdx || curLen + 1 == maxLength) {
                search.add(nextBeam[beamIdx], nextScores[i])
            } else if (wordIdx != sepIdx) {
                nextSentBeam.add(nextBeam[beamIdx].append(wordIdx) to nextScores[i])
            }
            if (nextSentBeam.size == beamSize) break
        }
        return nextSentBeam
    }

    private fun updateBeam(
        nextBeam: MutableList<List<Int>>,
        beamScores: MutableList<Float>,
        nextSentBeam: MutableList<Pair<List<Int>, Float>>
    ) {
        nextBeam.clear()
        beamScores.clear()
        for ((sentence, score) in nextSentBeam) {
            nextBeam.add(sentence)
            beamScores.add(score)
        }
    }

    private fun pessimizeScore(wordIdx: Int, score: Float, idxsToPessimize: Set<Int>) =
        if (repetitionPenalty != 1f && idxsToPessimize.contains(wordIdx))
            score * if (score < 0) repetitionPenalty else 1 / repetitionPenalty
        else score

    private fun getWordsToPessimize(beam: List<List<Int>>): List<Set<Int>> {
        return beam.map { getTokensWithDiffCasing(it) }
    }

    private fun getTokensWithDiffCasing(hyp: List<Int>) = hyp.subList(2, hyp.size).flatMap { tokenIdx ->
        val token = vocab.toWord(tokenIdx)
        val tokens1 = getDiffCasing(token)
        val tokens2 = if (token.endsWith("@@")) tokens1.map { it.replace("@@", "") } else tokens1.map { "$it@@" }
        vocab.toIndices(tokens1 + tokens2)
    }.toSet()

    private fun getDiffCasing(token: String) = listOf(token,
        token.uppercase(Locale.getDefault()),
        token.lowercase(Locale.getDefault()),
        token.replaceFirstChar { ch -> ch.uppercase() },
        token.replaceFirstChar { ch -> ch.lowercase() }
    )

    fun EncoderOutput.repeat(size: Int) = EncoderOutput(
        this.logProbs.repeat(size, model.env),
        this.lengths.repeat(size, model.env)
    )
}
