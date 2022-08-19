package org.jetbrains.iren.contributors

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.VariableNamesContributor
import org.jetbrains.iren.config.ModelType
import org.jetbrains.iren.services.DOBFModelManager
import org.jetbrains.iren.storages.VarNamePrediction
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.lang.String.join


class DOBFContributor : VariableNamesContributor {
    companion object {
        @JvmField
        val MODEL_PRIORITY = 1
    }

    override fun contribute(
        variable: PsiNameIdentifierOwner,
        selectedElement: PsiElement?,
        predictionList: MutableList<VarNamePrediction>
    ): Int {
        var language = variable.language
//        Kludge
        if (language.displayName == "Kotlin") language = JavaLanguage.INSTANCE
        val runner = DOBFModelManager.instance.get(language)
        predictionList.addAll(runner?.predict(variable) ?: return 0)
        return MODEL_PRIORITY
    }

    override fun getProbability(variable: PsiNameIdentifierOwner): Pair<Double, Int> {
//      get prob of the existing name. For now, it's used for debugging, so forget it.
        return Pair(0.0, 0)
    }

    override fun getModelType() = ModelType.DOBF
}