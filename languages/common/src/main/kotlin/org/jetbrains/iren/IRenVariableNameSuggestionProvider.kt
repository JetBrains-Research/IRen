package org.jetbrains.iren

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import org.jetbrains.iren.config.InferenceStrategies
import org.jetbrains.iren.services.IRenSuggestingService
import org.jetbrains.iren.storages.VarNamePrediction

class IRenVariableNameSuggestionProvider : NameSuggestionProvider {
    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {
        val project = element.project
        if (!(LanguageSupporter.getInstance(element.language)?.isVariableDeclaration(element) ?: return null)) return null
        val predictions = IRenSuggestingService.getInstance(project)
            .suggestVariableName(
                project,
                element as? PsiNameIdentifierOwner ?: return null,
                InferenceStrategies.ALL
            )
        result.addAll(predictions.map { it.name })
        return IRenSuggestedNameInfo(result.toTypedArray(), predictions)
    }

    class IRenSuggestedNameInfo(names: Array<String>, val predictions: List<VarNamePrediction>) :
        SuggestedNameInfo(names)
}