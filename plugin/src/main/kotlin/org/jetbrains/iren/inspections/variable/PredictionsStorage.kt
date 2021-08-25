package org.jetbrains.iren.inspections.variable

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiVariable
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.iren.IdNamesSuggestingService


class PredictionsStorage : Disposable {
    companion object {
        fun getInstance(): PredictionsStorage {
            return ServiceManager.getService(PredictionsStorage::class.java)
        }
    }

    private var storage: LoadingCache<SmartPsiElementPointer<PsiVariable>?, LinkedHashMap<String, Double>?> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .build(
            object : CacheLoader<SmartPsiElementPointer<PsiVariable>?, LinkedHashMap<String, Double>?>() {
                override fun load(key: SmartPsiElementPointer<PsiVariable>?): LinkedHashMap<String, Double>? { // no checked exception
                    if (key != null) {
                        return IdNamesSuggestingService.getInstance().suggestVariableName(key.element ?: return null)
                    }
                    return null
                }
            })

    fun getPrediction(pointer: SmartPsiElementPointer<PsiVariable>): LinkedHashMap<String, Double>?{
        return storage.get(pointer)
    }

    override fun dispose() {
        storage.cleanUp()
    }
}