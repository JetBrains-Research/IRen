package experiments.cacheSize

import com.intellij.openapi.project.Project
import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.ngram.PersistentNGramModelRunner

open class CacheSizePluginRunner : PluginRunner() {
    override val projectList = listOf("intellij-community")
    override val varNamer: VarNamer
        get() = CacheSizeVarNamer(saveDir, supporter, ngramType)

    override fun trainModelRunner(
        project: Project,
    ): NGramModelRunner {
        return PersistentNGramModelRunner()
    }
}