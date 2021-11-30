package tools.transformerEvaluator

import tools.modelsEvaluatorApi.PluginRunner
import tools.modelsEvaluatorApi.VarNamer

class TransformerPluginRunner : PluginRunner() {
    override val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    override val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override fun getCommandName(): String = "TransformerEvaluator"

    override val varNamer: VarNamer
        get() = TransformerVarNamer(saveDir, supporter, ngramType)
}
