package tools.compareWithDefault

import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.api.LanguageSupporter
import tools.ModelPrediction
import tools.modelsEvaluatorApi.VarNamer
import java.nio.file.Path

open class DefaultVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String
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