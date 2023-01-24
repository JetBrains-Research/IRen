package org.jetbrains.iren.utils

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

internal fun logSoftmax(scores: Array<FloatArray>): Array<FloatArray> {
    val expScores = Array(scores.size) { i ->
        val curScores = scores[i]
        val maxScore = curScores.maxOf { it }
        FloatArray(curScores.size) { j -> exp((curScores[j] - maxScore).toDouble()).toFloat() }
    }
    for (score in expScores) {
        val scoresSum = score.sum()
        for (i in score.indices) score[i] = ln((score[i] / scoresSum).toDouble()).toFloat()
    }
    return expScores
}

internal fun fastLogSoftmax(scores: Array<FloatArray>): Array<FloatArray> {
    val expScores = Array(scores.size) { i ->
        val curScores = scores[i]
        val maxScore = curScores.maxOf { it }
        FloatArray(curScores.size) { j -> fastExp((curScores[j] - maxScore).toDouble()).toFloat() }
    }
    for (score in expScores) {
        val scoresSum = score.sum()
        for (i in score.indices) score[i] = fastLn((score[i] / scoresSum).toDouble()).toFloat()
    }
    return expScores
}

fun fastExp(x: Double): Double {
    val tmp = (1512775 * x + 1072632447).toLong()
    return java.lang.Double.longBitsToDouble(tmp shl 32)
}

fun fastLn(x: Double): Double {
    val tmp = (java.lang.Double.doubleToLongBits(x) shr 32).toDouble()
    return (tmp - 1072632447) / 1512775
}

internal class MaxHeap(size: Int) {
    val data = FloatArray(size)
    val indices = IntArray(size)

    private var count = 0

    val minValue: Float
        get() = data[0]

    private val minIndex: Int
        get() = indices[0]

    private fun siftUp(idx: Int) {
        var internalIdx = idx
        while (data[internalIdx] < data[(internalIdx - 1) / 2]) {
            data.swap(internalIdx, (internalIdx - 1) / 2)
            indices.swap(internalIdx, (internalIdx - 1) / 2)
            internalIdx = (internalIdx - 1) / 2
        }
    }

    private fun siftDown(idx: Int) {
        var internalIdx = idx
        while (2 * internalIdx + 1 < count) {
            val left = 2 * internalIdx + 1
            val right = left + 1

            val j = if (right < count && data[right] < data[left]) right else left

            if (data[internalIdx] <= data[j])
                break

            data.swap(internalIdx, j)
            indices.swap(internalIdx, j)
            internalIdx = j
        }
    }

    fun insert(key: Float, index: Int) {
        count++
        indices[count - 1] = index
        data[count - 1] = key
        siftUp(count - 1)
    }

    fun removeMin() {
        data[0] = data[count - 1]
        indices[0] = indices[count - 1]
        count--
        siftDown(0)
    }

    fun sorted(): Pair<FloatArray, IntArray> {
        val sortedData = FloatArray(count)
        val sortedIndices = IntArray(count)

        for (idx in (count - 1) downTo 0) {
            sortedData[idx] = minValue
            sortedIndices[idx] = minIndex

            removeMin()
        }

        return sortedData to sortedIndices
    }

    fun clear() {
        count = 0
    }
}


internal fun FloatArray.swap(leftIdx: Int, rightIdx: Int) {
    val temp = get(leftIdx)
    this[leftIdx] = this[rightIdx]
    this[rightIdx] = temp
}

internal fun IntArray.swap(leftIdx: Int, rightIdx: Int) {
    val temp = get(leftIdx)
    this[leftIdx] = this[rightIdx]
    this[rightIdx] = temp
}

//TODO definitely there should be a better algorithm
internal fun topk1d(data: FloatArray, size: Int): Pair<FloatArray, IntArray> {
    val heapSize = min(size, data.size)
    val heap = MaxHeap(heapSize)

    repeat(heapSize) {
        heap.insert(data[it], it)
    }

    for (idx in heapSize until data.size) {
        val value = data[idx]
        if (value > heap.minValue) {
            heap.removeMin()
            heap.insert(value, idx)
        }
    }

    return heap.sorted()
}

infix fun Int.floorMod(other: Int) = ((this % other) + other) % other