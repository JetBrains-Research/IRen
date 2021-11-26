package tools.compareWithRAM

import tools.modelsEvaluatorApi.PluginRunner

class RamPluginRunner : PluginRunner() {
    override val projectList: List<String>? = listOf("libgdx")

    override fun getCommandName(): String = "compareWithRAM"

    override fun createVarNamer(): RamVarNamer {
        return RamVarNamer(saveDir, supporter, ngramType)
    }
}
