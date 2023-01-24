package experiments.transformerEvaluator

import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class TransformerPluginRunner : PluginRunner() {
    override val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    override val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override val varNamer: VarNamer
        get() = TransformerVarNamer(saveDir, supporter, ngramType)
}
