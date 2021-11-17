// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.iren.settings;

import com.intellij.openapi.options.Configurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.ngram.ModelBuilder;

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
        return "IRen";
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
        try {
            boolean modified = mySettingsComponent.getMaxTrainingTime() != settings.maxTrainingTime;
            modified |= mySettingsComponent.getAutomaticTrainingStatus() != settings.automaticTraining;
            modified |= mySettingsComponent.getVocabularyCutOff() != settings.vocabularyCutOff;
            modified |= mySettingsComponent.getModelsLifetime() != settings.modelsLifetime;
            modified |= mySettingsComponent.getModelsLifetimeUnit() != settings.modelsLifetimeUnit;
            return modified;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public void apply() {
        boolean needRetraining = needRetraining();
        AppSettingsState settings = AppSettingsState.getInstance();
        settings.automaticTraining = mySettingsComponent.getAutomaticTrainingStatus();
        settings.maxTrainingTime = mySettingsComponent.getMaxTrainingTime();
        settings.vocabularyCutOff = mySettingsComponent.getVocabularyCutOff();
        settings.modelsLifetime = mySettingsComponent.getModelsLifetime();
        settings.modelsLifetimeUnit = mySettingsComponent.getModelsLifetimeUnit();
        if (needRetraining) {
            ModelBuilder.trainModelsForAllProjectsInBackground();
        }
    }

    private boolean needRetraining() {
        AppSettingsState settings = AppSettingsState.getInstance();
//        Automatic training has been changed
        if (mySettingsComponent.getAutomaticTrainingStatus() != settings.automaticTraining)
            return mySettingsComponent.getAutomaticTrainingStatus();
//        Model training parameters have been changed
        return settings.automaticTraining &&
                (mySettingsComponent.getMaxTrainingTime() != settings.maxTrainingTime ||
                mySettingsComponent.getVocabularyCutOff() != settings.vocabularyCutOff);
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