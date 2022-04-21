package experiments.compareWithDefault

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiNameIdentifierOwner
import experiments.ModelPrediction
import experiments.modelsEvaluatorApi.VarNamer
import org.jetbrains.iren.LanguageSupporter
import org.jetbrains.iren.MyJavaNameSuggestionProvider
import java.nio.file.Path

open class DefaultVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String,
) : VarNamer(saveDir, supporter, ngramType) {
    override var runParallel = true

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val names = LinkedHashSet<String>()
        try {
            ReadAction.run<Exception> { MyJavaNameSuggestionProvider().getSuggestedNames(variable, variable, names) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return names.map { x: String -> ModelPrediction(x, 0.0) }
    }
}