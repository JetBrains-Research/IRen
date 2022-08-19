package org.jetbrains.iren.search

/**
 * Information regarding specific completion -- log probabilities and length in words.
 */
class GenerationInfo(initProbs: List<Float> = ArrayList(), initIds: List<Int> = ArrayList()) {
    var ids: IntArray = initIds.toIntArray()
        private set

    /**
     * Probabilities of BPE tokens one by one
     *
     * Note, that probability of the whole thing is a multiplication of all probabilities in [logProbs]
     */
    var logProbs: FloatArray = initProbs.toFloatArray()
        private set
}
