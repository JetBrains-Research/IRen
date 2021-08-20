// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.id.names.suggesting.settings;

import com.intellij.openapi.options.Configurable;


import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.LoadingTimeService;

import javax.swing.*;

/**
 * Provides controller functionality for application settings.
 */
public class AppSettingsConfigurable implements Configurable {

    private AppSettingsComponent mySettingsComponent;

    // A default constructor with no arguments is required because this implementation
    // is registered as an applicationConfigurable EP

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "IRen Settings";
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
        return mySettingsComponent.getPreferredFocusedComponent();
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        mySettingsComponent = new AppSettingsComponent();
        return mySettingsComponent.getPanel();
    }

    @Override
    public boolean isModified() {
        AppSettingsState settings = AppSettingsState.getInstance();
        boolean modified = mySettingsComponent.getMaxTrainingTime() != settings.maxTrainingTime;
        modified |= mySettingsComponent.getAutomaticTrainingStatus() != settings.automaticTraining;
        modified |= mySettingsComponent.getVocabularyCutOff() != settings.vocabularyCutOff;
        if (modified) {LoadingTimeService.getInstance().settingsChanged();}
        modified |= mySettingsComponent.getModelsLifetime() != settings.modelsLifetime;
        modified |= mySettingsComponent.getModelsLifetimeUnit() != settings.modelsLifetimeUnit;
        return modified;
    }

    @Override
    public void apply() {
        AppSettingsState settings = AppSettingsState.getInstance();
        settings.automaticTraining = mySettingsComponent.getAutomaticTrainingStatus();
        settings.maxTrainingTime = mySettingsComponent.getMaxTrainingTime();
        settings.vocabularyCutOff = mySettingsComponent.getVocabularyCutOff();
        settings.modelsLifetime = mySettingsComponent.getModelsLifetime();
        settings.modelsLifetimeUnit = mySettingsComponent.getModelsLifetimeUnit();
    }

    @Override
    public void reset() {
        AppSettingsState settings = AppSettingsState.getInstance();
        mySettingsComponent.setMaxTrainingTime(settings.maxTrainingTime);
        mySettingsComponent.setAutomaticTrainingStatus(settings.automaticTraining);
        mySettingsComponent.setVocabularyCutOff(settings.vocabularyCutOff);
        mySettingsComponent.setModelsLifetime(settings.modelsLifetime);
        mySettingsComponent.setModelsLifetimeUnit(settings.modelsLifetimeUnit);
    }

    @Override
    public void disposeUIResources() {
        mySettingsComponent = null;
    }

}