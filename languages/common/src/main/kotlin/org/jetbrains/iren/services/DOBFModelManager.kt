package org.jetbrains.iren.services

import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.iren.DOBFModelRunner

interface DOBFModelManager : Disposable {
    fun get(language: Language): DOBFModelRunner?
    fun put(language: Language, modelRunner: DOBFModelRunner)
    fun remove(language: Language)

    companion object {
        val instance: DOBFModelManager
            get() = ApplicationManager.getApplication().getService(DOBFModelManager::class.java)
    }
}