// Copyright 2000-2020 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.iren.settings;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.IRenBundle;
import org.jetbrains.iren.ModelBuilder;

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
        boolean modified = isModified();
        AppSettingsState settings = AppSettingsState.getInstance();
        settings.automaticTraining = mySettingsComponent.getAutomaticTrainingStatus();
        settings.maxTrainingTime = mySettingsComponent.getMaxTrainingTime();
        settings.vocabularyCutOff = mySettingsComponent.getVocabularyCutOff();
        settings.modelsLifetime = mySettingsComponent.getModelsLifetime();
        settings.modelsLifetimeUnit = mySettingsComponent.getModelsLifetimeUnit();
        if (settings.automaticTraining && modified) {
            ProgressManager.getInstance().run(new Task.Backgroundable(null, IRenBundle.message("training.task.title")) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                        indicator.setText(IRenBundle.message("training.progress.indexing"));
                        DumbService.getInstance(project).waitForSmartMode();
                        ModelBuilder.trainProjectNGramModel(project, indicator, true);
                    }
                }
            });
        }
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