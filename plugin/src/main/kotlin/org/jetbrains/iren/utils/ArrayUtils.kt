package org.jetbrains.iren.utils

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