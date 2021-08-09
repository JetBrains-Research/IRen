package tools.transformerEvaluator

import com.intellij.openapi.project.Project
import tools.modelsEvaluatorApi.PluginRunner
import java.nio.file.Path

class TransformerPluginRunner : PluginRunner() {
    override val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    override val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override fun getCommandName(): String = "TransformerEvaluator"

    override fun predict(project: Project, dir: Path, ngramContributorType: String) {
        TransformerVarNamer().predict(project, dir, ngramContributorType)
    }
}
