package tools.cacheSize

import com.intellij.openapi.project.Project
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.ngram.PersistentNGramModelRunner
import tools.modelsEvaluatorApi.PluginRunner
import tools.modelsEvaluatorApi.VarNamer

open class CacheSizePluginRunner : PluginRunner() {
    override fun getCommandName(): String = "cacheSize"
    override val projectList = listOf("intellij-community")
    override val varNamer: VarNamer
        get() = CacheSizeVarNamer(saveDir, supporter, ngramType)

    override fun trainModelRunner(
        project: Project,
    ): NGramModelRunner {
        return PersistentNGramModelRunner()
    }
}