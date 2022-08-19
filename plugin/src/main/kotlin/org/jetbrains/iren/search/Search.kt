package org.jetbrains.iren.search

import org.jetbrains.iren.storages.Vocabulary

internal abstract class Search(
    val vocab: Vocabulary,
    val searchSize: Int,
    val repetitionPenalty: Float = 1.0f
) {

    internal data class StepResult(val sortMask: IntArray, val newTokens: IntArray)

    /**
     * Current batch size
     */
    abstract val batchSize: Int

    abstract fun step(stepLogProbs: Array<FloatArray>): StepResult

    /**
     * List of current hypotheses
     */
    abstract fun hypotheses(): List<List<Int>>

    /**
     * Tensor of last tokens of the current hypotheses with shape (batch_size,) to make a batch for a model
     */
    abstract fun lastPredictions(): IntArray

    /**
     * Scores of hypotheses
     */
    abstract fun scores(): List<Float>
}
