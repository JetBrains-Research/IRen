package org.jetbrains.iren.settings

import com.intellij.openapi.options.Configurable
import org.jetbrains.annotations.Nls
import org.jetbrains.iren.settings.AppSettingsState.Companion.getInstance
import org.jetbrains.iren.training.ModelBuilder
import javax.swing.JComponent

/**
 * Provides controller functionality for application settings.
 */
class AppSettingsConfigurable : Configurable {
    private var mySettingsComponent: AppSettingsComponent? = null

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP
    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String {
        return "IRen"
    }

    override fun createComponent(): JComponent {
        mySettingsComponent = AppSettingsComponent()
        return mySettingsComponent!!.panel
    }

    override fun isModified(): Boolean {
        val settings = getInstance()
        return try {
            var modified = mySettingsComponent!!.maxTrainingTime != settings.maxTrainingTime
            modified = modified or (mySettingsComponent!!.automaticTrainingStatus != settings.automaticTraining)
            modified = modified or (mySettingsComponent!!.vocabularyCutOff != settings.vocabularyCutOff)
            modified = modified or (mySettingsComponent!!.modelsLifetime != settings.modelsLifetime)
            modified = modified or (mySettingsComponent!!.modelsLifetimeUnit != settings.modelsLifetimeUnit)
            modified
        } catch (e: NumberFormatException) {
            false
        }
    }

    override fun apply() {
        val needRetraining = needRetraining()
        val settings = getInstance()
        settings.automaticTraining = mySettingsComponent!!.automaticTrainingStatus
        settings.maxTrainingTime = mySettingsComponent!!.maxTrainingTime
        settings.vocabularyCutOff = mySettingsComponent!!.vocabularyCutOff
        settings.modelsLifetime = mySettingsComponent!!.modelsLifetime
        settings.modelsLifetimeUnit = mySettingsComponent!!.modelsLifetimeUnit
        if (needRetraining) {
            ModelBuilder.trainModelsForAllProjectsInBackground()
        }
    }

    private fun needRetraining(): Boolean {
        val settings = getInstance()
        //        Automatic training has been changed
        return if (mySettingsComponent!!.automaticTrainingStatus != settings.automaticTraining) mySettingsComponent!!.automaticTrainingStatus else settings.automaticTraining &&
                (mySettingsComponent!!.maxTrainingTime != settings.maxTrainingTime ||
                        mySettingsComponent!!.vocabularyCutOff != settings.vocabularyCutOff)
        //        Model training parameters have been changed
    }

    override fun reset() {
        val settings = getInstance()
        mySettingsComponent!!.maxTrainingTime = settings.maxTrainingTime
        mySettingsComponent!!.automaticTrainingStatus = settings.automaticTraining
        mySettingsComponent!!.vocabularyCutOff = settings.vocabularyCutOff
        mySettingsComponent!!.modelsLifetime = settings.modelsLifetime
        mySettingsComponent!!.modelsLifetimeUnit = settings.modelsLifetimeUnit
    }

    override fun disposeUIResources() {
        mySettingsComponent = null
    }
}