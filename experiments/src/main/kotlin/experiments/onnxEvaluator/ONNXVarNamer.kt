package experiments.onnxEvaluator

import com.intellij.psi.PsiNameIdentifierOwner
import experiments.ModelPrediction
import experiments.ModelPredictions
import experiments.modelsEvaluatorApi.VarNamer
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.models.OrtModelRunner
import org.jetbrains.iren.utils.DOBFModelUtils
import java.nio.file.Path

class ONNXVarNamer(saveDir: Path, supporter: LanguageSupporter, ngramType: String) :
    VarNamer(saveDir, supporter, ngramType) {
    val modelDir = Path.of("/home/igor/IdeaProjects/IRen/plugin/build/idea-sandbox/system/DOBF_models", DOBFModelUtils().getName(supporter.language))
    private val runner = OrtModelRunner(modelDir, maxSequenceLength = 512, cacheSize = 0L)
    override var runParallel = false

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val start = System.nanoTime()
        val predictions = runner.predict(variable)
        return ModelPredictions(
            predictions.sortedBy { -it.probability }.take(10).map { ModelPrediction(it.name, it.probability) },
            (System.nanoTime() - start) / 1e9
        )
    }
}