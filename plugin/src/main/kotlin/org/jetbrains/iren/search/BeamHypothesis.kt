package org.jetbrains.iren.search

import kotlin.math.min
import kotlin.math.pow


class BeamHypothesis(
    val beamSize: Int,
    val maxLength: Int,
    val lengthPenalty: Float,
    val earlyStopping: Boolean
) {
    val hyps = mutableListOf<Hypothesis>()
    var worstScore = 1e9f

    fun add(hyp: List<Int>, sumLogProbs: Float) {
        val score = sumLogProbs / hyp.size.toFloat().pow(lengthPenalty)
        if (hyps.size < beamSize || score > worstScore) {
            hyps.add(Hypothesis(hyp, score))
            if (hyps.size > beamSize) {
                val sortedScores = hyps.withIndex().sortedBy { it.value.score }
                hyps.removeAt(sortedScores[0].index)
                worstScore = sortedScores[1].value.score
            } else {
                worstScore = min(score, worstScore)
            }
        }
    }

    fun isDone(bestSumLogProbs: Float): Boolean {
        return hyps.size >= beamSize && (earlyStopping || worstScore >= bestSumLogProbs / maxLength.toFloat()
            .pow(lengthPenalty))
    }
}

class Hypothesis(val hyp: List<Int>, val score: Float)