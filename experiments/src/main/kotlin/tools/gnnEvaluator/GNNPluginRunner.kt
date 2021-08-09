package tools.gnnEvaluator

import com.intellij.openapi.project.Project
import tools.modelsEvaluatorApi.PluginRunner
import java.nio.file.Path

class GNNPluginRunner : PluginRunner() {
    override val javaSmallTrain = listOf(
        "cassandra", "elasticsearch", "gradle", "hibernate-orm", "intellij-community",
        "liferay-portal", "presto", "spring-framework", "wildfly"
    )
    override val javaSmallTest =
        listOf("libgdx", "hadoop")
//        listOf("TestProject")

    override fun getCommandName(): String = "GNNEvaluator"

    override fun predict(project: Project, dir: Path, ngramContributorType: String) {
        GNNVarNamer().predict(project, dir, ngramContributorType)
    }
}
