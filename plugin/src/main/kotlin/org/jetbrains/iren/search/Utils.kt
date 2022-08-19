package org.jetbrains.iren.search

import kotlin.math.*

internal fun IntArray.toLongArray(): LongArray {
    return LongArray(size) { this[it].toLong() }
}

internal fun Array<IntArray>.toLongArray(): LongArray {
    val arr = LongArray(this.sumOf { it.size })
    var off = 0
    for (block in this) {
        for (value in block) arr[off++] = value.toLong()
    }
    return arr
}

internal fun IntArray.sliceArray(indices: IntArray): IntArray {
    val result = IntArray(indices.size)
    var targetIndex = 0
    for (sourceIndex in indices) {
        result[targetIndex++] = this[sourceIndex]
    }
    return result
}

internal fun <T> List<T>.slice(indices: IntArray): List<T> {
    val result = ArrayList<T>(indices.size)
    for ((targetIndex, sourceIndex) in indices.withIndex()) {
        result.add(targetIndex, this[sourceIndex])
    }
    return result
}

internal fun logSoftmax(scores: Array<FloatArray>): Array<FloatArray> {
    val expScores = Array(scores.size) {
        val curScores = scores[it]
        FloatArray(curScores.size) { i -> exp(curScores[i]) }
    }
    for (score in expScores) {
        val scoresSum = score.sum()
        for (i in score.indices) score[i] = ln(score[i] / scoresSum)
    }
    return expScores
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

internal fun topk2d(data: Array<FloatArray>, size: Int, dim: Int = 0): Array<IntArray> {
    if (data.isEmpty()) {
        return emptyArray()
    }

    when (dim) {
        0 -> {
            val listSize = min(data.size, size)
            val result = Array(listSize) { IntArray(data[0].size) }
            for (j in data[0].indices) {
                val slice = FloatArray(data.size) { data[it][j] }
                val (_, topColumnIndices) = topk1d(slice, size)
                for (i in topColumnIndices.indices) result[i][j] = topColumnIndices[i]
            }
            return result
        }
        1 -> {
            return Array(data.size) { topk1d(data[it], size).second }
        }
        else -> {
            throw IllegalArgumentException("Index should be 0 or 1")
        }
    }
}

infix fun Double.floorMod(other: Double) = ((this % other) + other) % other

infix fun Int.floorMod(other: Int) = ((this % other) + other) % other

infix fun Double.floorMod(other: Int) = ((this % other) + other) % other

infix fun Int.floorMod(other: Double) = ((this % other) + other) % other

fun Int.floorDiv(other: Int): Int {
    var q = this / other
    if (this xor other < 0 && q * other != this) q--
    return q
}
