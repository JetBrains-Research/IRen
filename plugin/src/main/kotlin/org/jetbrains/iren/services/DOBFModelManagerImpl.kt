package org.jetbrains.iren.services

import com.intellij.lang.Language
import org.jetbrains.iren.DOBFModelRunner

val DOBF_CURRENT_VERSION = 1

fun getModelName(language: Language) = "${language.displayName}_$DOBF_CURRENT_VERSION"

class DOBFModelManagerImpl : DOBFModelManager {
    private val myModelRunners: MutableMap<String, DOBFModelRunner> = HashMap()

    override fun get(language: Language): DOBFModelRunner? {
        return myModelRunners[getModelName(language)]
    }

    override fun put(language: Language, modelRunner: DOBFModelRunner) {
        myModelRunners[getModelName(language)] = modelRunner
    }

    override fun remove(language: Language) {
        myModelRunners.remove(getModelName(language))
    }

    override fun dispose() {
        myModelRunners.clear()
    }
}