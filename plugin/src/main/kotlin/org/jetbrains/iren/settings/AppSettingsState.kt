package org.jetbrains.iren.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil
import java.time.temporal.ChronoUnit

/**
 * Supports storing the application settings in a persistent way.
 * The {@link State} and {@link Storage} annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "AppSettingsState", storages = [Storage("IRenPluginSettings.xml")])
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var firstOpen: Boolean = true
    var automaticTraining: Boolean = true
    var maxTrainingTime: Int = 300
    var vocabularyCutOff: Int = 3
    var modelsLifetime: Int = 1
    var modelsLifetimeUnit: ChronoUnit = ChronoUnit.DAYS

    companion object {
        @JvmStatic
        fun getInstance(): AppSettingsState = service()
    }

    override fun getState() = this

    override fun loadState(state: AppSettingsState) = XmlSerializerUtil.copyBean(state, this)
}