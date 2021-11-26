package tools.compareWithRAM

import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.api.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.services.ModelManager
import org.jetbrains.iren.storages.VarNamePrediction
import tools.ModelPrediction
import tools.modelsEvaluatorApi.VarNamer
import java.nio.file.Path
import kotlin.concurrent.thread

open class RamVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String
) : VarNamer(saveDir, supporter, ngramType) {
    private val RamModelRunners: ArrayList<NGramModelRunner> by lazy { prepareRunners() }
    private fun prepareRunners(): ArrayList<NGramModelRunner> {
        val modelDir = saveDir.resolve("tmp_model")
        myModelRunner.save(modelDir, null)
        println("Number of threads: $maxNumberOfThreads")
        val res = ArrayList<NGramModelRunner>()
        (0 until maxNumberOfThreads).map {
            thread {
                println("Preparing ${it + 1}-th runner")
                val runner = NGramModelRunner(true, 6)
                runner.load(modelDir, null)
                res.add(runner)
            }
        }.forEach { it.join() }
        return res
    }

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val runner = RamModelRunners[thread]
        ModelManager.getInstance().forgetFileIfNeeded(runner, variable.containingFile)
        val nameSuggestions: List<VarNamePrediction> = runner.suggestNames(variable)
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }
}