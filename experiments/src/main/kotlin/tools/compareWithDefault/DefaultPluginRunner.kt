package tools.compareWithDefault

import tools.modelsEvaluatorApi.PluginRunner
import tools.modelsEvaluatorApi.VarNamer

class DefaultPluginRunner : PluginRunner() {
    override val projectList: List<String>? = listOf("libgdx")// listOf("intellij-community")

    override fun getCommandName(): String = "compareWithDefault"

    override fun createVarNamer(): VarNamer {
        return DefaultVarNamer(saveDir, supporter, ngramType)
    }
}
