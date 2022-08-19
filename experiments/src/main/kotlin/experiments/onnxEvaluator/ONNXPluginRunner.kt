package experiments.onnxEvaluator

import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class ONNXPluginRunner : PluginRunner() {
    override fun getCommandName(): String = "ONNXEvaluator"

    override val projectList: List<String>? = null
//        get() = saveDir.parent.resolve("java-med-onnx-2048").listDirectoryEntries("*_BiDirectional.jsonl")
//            .map { it.name.replace("_BiDirectional.jsonl", "") }
    override val resumeEvaluation: Boolean = true

    override val varNamer: VarNamer
        get() = ONNXVarNamer(saveDir, supporter, ngramType)
}
