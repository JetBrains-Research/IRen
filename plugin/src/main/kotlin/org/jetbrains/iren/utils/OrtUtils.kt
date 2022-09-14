package org.jetbrains.iren.utils

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import io.kinference.ndarray.toLongArray
import java.nio.FloatBuffer
import java.nio.LongBuffer

const val PAD_IDX = 2

fun prepareInput(idxs: List<List<Int>>, env: OrtEnvironment): Pair<OnnxTensor, OnnxTensor> {
    val maxLength = idxs.maxOf { it.size }
    val array =
        Array(maxLength * idxs.size) { idxs[it.floorDiv(maxLength)].getOrElse(it.floorMod(maxLength)) { PAD_IDX } }
    val x = OnnxTensor.createTensor(
        env, LongBuffer.wrap(array.toIntArray().toLongArray()), longArrayOf(idxs.size.toLong(), maxLength.toLong())
    )
    val lengths = OnnxTensor.createTensor(env, LongArray(idxs.size) { idxs[it].size.toLong() })
    return Pair(x, lengths)
}



fun tensorToArrays2D(logProbs: OnnxTensor): Array<FloatArray> {
    val logProbArray = logProbs.floatBuffer.array()
    val shape = logProbs.info.shape
    return Array(shape[0].toInt()) {
        logProbArray.sliceArray(it * shape[1].toInt() until (it + 1) * shape[1].toInt())
    }
}

/**
 * Repeat tensor on first dimension [n] times. Works with long and float values.
 **/
fun OnnxTensor.repeat(n: Int, env: OrtEnvironment): OnnxTensor {
    val shape = this.info.shape
    val newShape = shape.copyOf()
    newShape[0] = newShape[0] * n
    this.floatBuffer?.let { buffer ->
        val data = buffer.array()
        buffer.limit()
        return OnnxTensor.createTensor(
            env,
            FloatBuffer.wrap(FloatArray(data.size * n) { data[it.floorMod(data.size)] }), newShape
        )
    }
    val data = this.longBuffer.array()
    return OnnxTensor.createTensor(
        env,
        LongBuffer.wrap(LongArray(data.size * n) { data[it.floorMod(data.size)] }),
        newShape
    )
}
