// Copyright 2000-2021 JetBrains s.r.o. and other contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.iren.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.temporal.ChronoUnit;

/**
 * Supports storing the application settings in a persistent way.
 * The {@link State} and {@link Storage} annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(
        name = "AppSettingsState",
        storages = {@Storage("IRenPluginSettings.xml")}
)
public class AppSettingsState implements PersistentStateComponent<AppSettingsState> {
    public boolean firstOpen = true;
    public boolean automaticTraining = true;
    public boolean sendStatistics = true;
    public int maxTrainingTime = 30;
    public int vocabularyCutOff = 0;
    public int modelsLifetime = 1;
    public ChronoUnit modelsLifetimeUnit = ChronoUnit.DAYS;

    public static AppSettingsState getInstance() {
        return ApplicationManager.getApplication().getService(AppSettingsState.class);
    }

    @Nullable
    @Override
    public AppSettingsState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull AppSettingsState state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
