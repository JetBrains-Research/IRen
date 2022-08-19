package experiments.compareWithRAM

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiNameIdentifierOwner
import experiments.ModelPrediction
import experiments.modelsEvaluatorApi.VarNamer
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.ngram.NGramModelRunner
import org.jetbrains.iren.services.NGramModelManager
import org.jetbrains.iren.storages.VarNamePrediction
import java.nio.file.Path

open class RamVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String
) : VarNamer(saveDir, supporter, ngramType) {
    private val ramModelRunners: List<NGramModelRunner> by lazy { prepareRunners() }
    private fun prepareRunners(): List<NGramModelRunner> {
        val modelDir = saveDir.resolve("tmp_model")
        myModelRunner.save(modelDir, null)
        return (0 until maxNumberOfThreads).map {
            println("Preparing ${it + 1}-th runner")
            val runner = NGramModelRunner()
            runner.load(modelDir, null)
            runner
        }
    }

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val runner = ramModelRunners[thread]
        ReadAction.run<Exception> { NGramModelManager.getInstance(variable.project).forgetFileIfNeeded(runner, variable.containingFile) }
        val nameSuggestions: List<VarNamePrediction> =
            ReadAction.compute<List<VarNamePrediction>, Exception> { runner.suggestNames(variable) }
        return nameSuggestions.map { x: VarNamePrediction -> ModelPrediction(x.name, x.probability) }
    }
}