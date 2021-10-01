package org.jetbrains.iren.inspections.variable

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.intellij.completion.ngram.slp.translating.Vocabulary
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.iren.IRenSuggestingService
import java.util.concurrent.TimeUnit


class ConsistencyChecker : Disposable {
    companion object {
        fun getInstance(): ConsistencyChecker {
            return ApplicationManager.getApplication().getService(ConsistencyChecker::class.java)
        }
    }

    private var storage: LoadingCache<SmartPsiElementPointer<PsiNameIdentifierOwner>?, Boolean?> =
        CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(
                object : CacheLoader<SmartPsiElementPointer<PsiNameIdentifierOwner>?, Boolean?>() {
                    override fun load(key: SmartPsiElementPointer<PsiNameIdentifierOwner>?): Boolean? { // no checked exception
                        val variable = key?.element ?: return null
                        return isGoodPredictionList(
                            variable,
                            IRenSuggestingService.getInstance().suggestVariableName(variable)
                        )
                    }
                })

    fun isInconsistent(pointer: SmartPsiElementPointer<PsiNameIdentifierOwner>): Boolean {
        return storage.get(pointer) ?: false
    }

    override fun dispose() {
        storage.invalidateAll()
    }

    fun isGoodPredictionList(variable: PsiNameIdentifierOwner, predictions: LinkedHashMap<String, Double>): Boolean {
        val varThreshold = predictions[variable.name]
        val vocabThreshold = predictions[Vocabulary.unknownCharacter]
        return (if (varThreshold != null) predictions.filterValues { v -> v > varThreshold } else predictions).size >= 5 &&
                (if (vocabThreshold != null) predictions.filterValues { v -> v > vocabThreshold } else predictions).isNotEmpty()
    }
}