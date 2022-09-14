package org.jetbrains.iren.contributors

import com.intellij.lang.java.JavaLanguage
import com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.iren.VariableNamesContributor
import org.jetbrains.iren.config.ModelType
import org.jetbrains.iren.services.DOBFModelManager
import org.jetbrains.iren.storages.VarNamePrediction


class DOBFContributor : VariableNamesContributor {
    companion object {
        @JvmField
        val MODEL_PRIORITY = 1.0
    }

    override fun contribute(
        variable: PsiNameIdentifierOwner,
        predictionList: MutableList<VarNamePrediction>
    ): Double {
        var language = variable.language
//        Kludge
        if (language.displayName == "Kotlin") language = JavaLanguage.INSTANCE
        val runner = DOBFModelManager.instance.get(language)
        predictionList.addAll(runner?.predict(variable) ?: return .0)
        return MODEL_PRIORITY
    }

    override fun getProbability(variable: PsiNameIdentifierOwner): Pair<Double, Double> {
//      get prob of the existing name. For now, it's used for debugging, so forget it.
        return Pair(.0, .0)
    }

    override fun getModelType() = ModelType.DOBF
}