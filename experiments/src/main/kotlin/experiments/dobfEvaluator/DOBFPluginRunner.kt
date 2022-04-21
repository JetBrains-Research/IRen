package experiments.dobfEvaluator

import com.intellij.completion.ngram.slp.counting.trie.persistent.CountersCache
import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class DOBFPluginRunner : PluginRunner() {

    override val projectList = null
    override val resumeEvaluation = true

    override fun getCommandName(): String = "DOBFEvaluator"

    override val varNamer: VarNamer
        get() {
            CountersCache.MAXIMUM_CACHE_SIZE = 10_000
            return DOBFVarNamer(saveDir, supporter, ngramType)
        }
}
