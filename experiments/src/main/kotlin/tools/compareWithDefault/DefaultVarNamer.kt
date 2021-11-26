package tools.compareWithDefault

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.iren.api.LanguageSupporter
import tools.ModelPrediction
import tools.modelsEvaluatorApi.VarNamer
import java.nio.file.Path

open class DefaultVarNamer(
    saveDir: Path,
    supporter: LanguageSupporter,
    ngramType: String
) : VarNamer(saveDir, supporter, ngramType) {
    override var runParallel = false

    override fun predictWithNN(variable: PsiNameIdentifierOwner, thread: Int): Any {
        val names = LinkedHashSet<String>()
        val oldName = variable.name!!
        try {
            WriteCommandAction.runWriteCommandAction(variable.project) {
                variable.setName("_") as PsiNameIdentifierOwner
            }
            NameSuggestionProvider.suggestNames(variable, variable, names)
            WriteCommandAction.runWriteCommandAction(variable.project) {
                variable.setName(oldName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return names.filter { it != "_" && it != "" }.map { x: String -> ModelPrediction(x, 0.0) }
    }
}