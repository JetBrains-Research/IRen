package experiments.compareWithRAM

import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class RamPluginRunner : PluginRunner() {
    override val projectList: List<String>? = listOf("libgdx")

    override val varNamer: VarNamer
        get() = RamVarNamer(saveDir, supporter, ngramType)
}
