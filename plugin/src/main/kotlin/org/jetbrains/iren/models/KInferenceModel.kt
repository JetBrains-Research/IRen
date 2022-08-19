package org.jetbrains.iren.models

import io.kinference.core.KIEngine
import io.kinference.core.data.tensor.KITensor
import io.kinference.core.data.tensor.asTensor
import io.kinference.model.Model
import io.kinference.ndarray.Strides
import io.kinference.ndarray.arrays.FloatNDArray
import io.kinference.ndarray.arrays.LongNDArray
import io.kinference.ndarray.arrays.tiled.LongTiledArray
import io.kinference.ndarray.toLongArray
import java.nio.file.Path

class KInferenceModel(encoderPath: Path, decoderPath: Path) {
    val encoder = Model.load(encoderPath.toFile().readBytes(), KIEngine)
    val decoder = Model.load(decoderPath.toFile().readBytes(), KIEngine)

    fun encode(idxs: IntArray): Pair<FloatNDArray, LongNDArray> {
//        B=1 - batch size, S - source length, T - target length, E=256 - embeddings size, V=64000 - vocabulary size
//        All shapes from debugging
        val longIds = LongTiledArray(arrayOf(idxs.toLongArray()))
        val inputIds = LongNDArray(longIds, Strides(intArrayOf(1, idxs.size)))
        val inputLengths = LongNDArray(intArrayOf(1)) { idxs.size.toLong() }

        val input = ArrayList<KITensor>()
        input.add(inputIds.asTensor("x"))  // Shape: [B, S]
        input.add(inputLengths.asTensor("lengths"))  // Shape: [B]

        return (encoder.predict(input)["output"] as KITensor).data as FloatNDArray to inputLengths // Shape: [B, S, E]
    }

    fun decode(idxs: LongNDArray, src_enc: FloatNDArray, src_len: LongNDArray): FloatNDArray {
        val lengths = LongNDArray(intArrayOf(idxs.shape[0])) { idxs.shape[1].toLong() }  // Shape: [B]

        val decInput = ArrayList<KITensor>()
//        ['x', 'lengths', 'src_enc', 'src_len']
        decInput.add(idxs.asTensor("x"))  // Shape: [B, T]
        decInput.add(lengths.asTensor("lengths"))  // Shape: [B]
        decInput.add(src_enc.asTensor("src_enc"))  // Shape: [B, S, E]
        decInput.add(src_len.asTensor("src_len"))  // Shape: [B]

        return (decoder.predict(decInput)["output"] as KITensor).data as FloatNDArray
    }
}