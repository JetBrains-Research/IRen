package experiments.dobfEvaluator

import com.intellij.completion.ngram.slp.counting.trie.my.persistent.CountersCache
import experiments.modelsEvaluatorApi.PluginRunner
import experiments.modelsEvaluatorApi.VarNamer

class DOBFPluginRunner : PluginRunner() {

    override val projectList = null
    override val resumeEvaluation = true

    override val varNamer: VarNamer
        get() {
            CountersCache.MAXIMUM_CACHE_SIZE = 10_000
            return DOBFVarNamer(saveDir, supporter, ngramType)
        }
}
