package experiments.compareWithDefault

import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class DefaultPluginRunner : PluginRunner() {
    override val projectList: List<String>? = null // listOf("libgdx")// listOf("intellij-community")

    override fun getCommandName(): String = "compareWithDefault"

    override val varNamer: VarNamer
        get() = DefaultVarNamer(saveDir, supporter, ngramType)
}
