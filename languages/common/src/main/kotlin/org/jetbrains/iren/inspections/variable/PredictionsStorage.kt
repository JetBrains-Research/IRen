package org.jetbrains.iren.inspections.variable

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.iren.IRenSuggestingService
import java.util.concurrent.TimeUnit


class PredictionsStorage : Disposable {
    companion object {
        fun getInstance(): PredictionsStorage {
            return ServiceManager.getService(PredictionsStorage::class.java)
        }
    }

    private var storage: LoadingCache<SmartPsiElementPointer<PsiNameIdentifierOwner>?, LinkedHashMap<String, Double>?> = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build(
            object : CacheLoader<SmartPsiElementPointer<PsiNameIdentifierOwner>?, LinkedHashMap<String, Double>?>() {
                override fun load(key: SmartPsiElementPointer<PsiNameIdentifierOwner>?): LinkedHashMap<String, Double>? { // no checked exception
                    return IRenSuggestingService.getInstance().suggestVariableName(key?.element ?: return null)
                }
            })

    fun getPrediction(pointer: SmartPsiElementPointer<PsiNameIdentifierOwner>): LinkedHashMap<String, Double>{
        return storage.get(pointer) ?: LinkedHashMap()
    }

    override fun dispose() {
        storage.invalidateAll()
    }
}