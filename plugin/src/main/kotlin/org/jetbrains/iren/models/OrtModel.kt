package org.jetbrains.iren.models

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.jetbrains.iren.utils.prepareInput
import java.nio.file.Path

class OrtModel(encoderPath: Path, decoderPath: Path) {
    val env = OrtEnvironment.getEnvironment()
    val encoderSession = env.createSession(encoderPath.toFile().readBytes(), OrtSession.SessionOptions())
    val decoderSession = env.createSession(decoderPath.toFile().readBytes(), OrtSession.SessionOptions())

    fun encode(idxs: List<List<Int>>): EncoderOutput {
        val (x, lengths) = prepareInput(idxs, env)

        val input = mapOf("x" to x, "lengths" to lengths)

        val res = encoderSession.run(input)["output"].get() as OnnxTensor
        return EncoderOutput(res, lengths)
    }

    fun decode(idxs: List<List<Int>>, encoderOutput: EncoderOutput): OnnxTensor {
        val (x, lengths) = prepareInput(idxs, env)
        val decInput2 = mutableMapOf(
            "x" to x, "lengths" to lengths, "src_enc" to encoderOutput.logProbs, "src_len" to encoderOutput.lengths
        )
        return decoderSession.run(decInput2)["output"].get() as OnnxTensor
    }
}

data class EncoderOutput(val logProbs: OnnxTensor, val lengths: OnnxTensor)