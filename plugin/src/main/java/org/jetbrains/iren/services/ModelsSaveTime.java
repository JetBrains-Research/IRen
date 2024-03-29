package org.jetbrains.iren.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.settings.AppSettingsState;
import org.jetbrains.iren.utils.ModelUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.iren.utils.IdeaUtil.isIdeaProject;

@State(name = "ModelsSaveTime",
        storages = {@Storage("IRenModelsSaveTime.xml")})
public class ModelsSaveTime implements PersistentStateComponent<ModelsSaveTime> {
    @XMap(propertyElementName = "saveTime", keyAttributeName = "model", valueAttributeName = "time")
    public final Map<String, String> mySavingTime = new HashMap<>();

    public static @NotNull ModelsSaveTime getInstance() {
        return ApplicationManager.getApplication().getService(ModelsSaveTime.class);
    }

    @Override
    public @Nullable ModelsSaveTime getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ModelsSaveTime state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void setTrainedTime(@NotNull Project project) {
        mySavingTime.put(new ModelUtils().getName(project, null), LocalDateTime.now().toString());
    }

    public boolean needRetraining(@NotNull Project project) {
        if (isIdeaProject(project)) return false;
        @Nullable LocalDateTime saveTime = whenTrained(project);
        AppSettingsState settings = AppSettingsState.getInstance();
        return (saveTime == null ||
                settings.getModelsLifetimeUnit().between(saveTime, LocalDateTime.now()) >= settings.getModelsLifetime());
    }

    public @Nullable LocalDateTime whenTrained(@NotNull Project project) {
        String str = mySavingTime.get(new ModelUtils().getName(project, null));
        try {
            return str == null ? null : LocalDateTime.parse(str);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
