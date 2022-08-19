package org.jetbrains.iren.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.OrtUtil
import io.kinference.ndarray.toLongArray
import org.jetbrains.iren.search.floorMod
import java.nio.file.Path

class OrtModel(encoderPath: Path, decoderPath: Path) {
    private val PAD_IDX = 2
    val env = OrtEnvironment.getEnvironment()
    val encoderSession = env.createSession(encoderPath.toFile().readBytes(), OrtSession.SessionOptions())
    val decoderSession = env.createSession(decoderPath.toFile().readBytes(), OrtSession.SessionOptions())

    fun encode(idxs: List<List<Int>>): EncoderOutput {
        val (x, lengths) = prepareInput(idxs)

        val input = mapOf("x" to x, "lengths" to lengths)

        val res = encoderSession.run(input)["output"].get() as OnnxTensor
        return EncoderOutput(res, lengths)
    }

    fun decode(idxs: List<List<Int>>, encoderOutput: EncoderOutput): OnnxTensor {
        val (x, lengths) = prepareInput(idxs)
        val decInput2 = mutableMapOf(
            "x" to x,
            "lengths" to lengths,
            "src_enc" to encoderOutput.logProbs,
            "src_len" to encoderOutput.lengths
        )
        return decoderSession.run(decInput2)["output"].get() as OnnxTensor
    }

    private fun prepareInput(idxs: List<List<Int>>): Pair<OnnxTensor, OnnxTensor> {
//        TODO: remove it from the model to another place
        val maxLength = idxs.maxOf { it.size }
        val array =
            Array(maxLength * idxs.size) { idxs[it.floorDiv(maxLength)].getOrElse(it.floorMod(maxLength)) { PAD_IDX } }
        val x = OnnxTensor.createTensor(
            env,
            OrtUtil.reshape(array.toIntArray().toLongArray(), longArrayOf(idxs.size.toLong(), maxLength.toLong()))
        )
        val lengths = OnnxTensor.createTensor(env, LongArray(idxs.size) { idxs[it].size.toLong() })
        return Pair(x, lengths)
    }
}

data class EncoderOutput(val logProbs: OnnxTensor, val lengths: OnnxTensor)