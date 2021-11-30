package tools.gnnEvaluator

import tools.modelsEvaluatorApi.PluginRunner
import tools.modelsEvaluatorApi.VarNamer

class GNNPluginRunner : PluginRunner() {
    override val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    override val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override fun getCommandName(): String = "GNNEvaluator"

    override val varNamer: VarNamer
        get() = GNNVarNamer(saveDir, supporter, ngramType)
}
