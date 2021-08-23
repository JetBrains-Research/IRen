package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.settings.AppSettingsState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.id.names.suggesting.utils.StringUtils.join;

@State(name = "org.jetbrains.id.names.suggesting.ModelSaveTimeService",
storages = {@Storage("ModelSaveTime.xml")})
public class ModelSaveTimeService implements PersistentStateComponent<ModelSaveTimeService> {
    private boolean isTraining = false;
    @XMap(propertyElementName = "saveTime", keyAttributeName = "model", valueAttributeName = "time")
    public final Map<String, String> mySavingTime = new HashMap<>();

    @Override
    public @Nullable ModelSaveTimeService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ModelSaveTimeService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void setTraining(boolean training) {
        isTraining = training;
    }


    public static @NotNull ModelSaveTimeService getInstance() {
        return ServiceManager.getService(ModelSaveTimeService.class);
    }

    public void setTrained(@NotNull Class<? extends VariableNamesContributor> className, boolean b) {
        if (b) {
            mySavingTime.put(className.getSimpleName(), Instant.now().toString());
        } else {
            mySavingTime.remove(className.getSimpleName());
        }
    }

    public void setTrained(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, boolean b) {
        if (b) {
            mySavingTime.put(join(className, project), Instant.now().toString());
        } else {
            mySavingTime.remove(join(className, project));
        }
    }

    public boolean isSomethingLoaded() {
        return !mySavingTime.isEmpty();
    }

    public boolean isTrained(Class<? extends VariableNamesContributor> className) {
        return mySavingTime.containsKey(className.getSimpleName());
    }

    public boolean isTrained(Class<? extends VariableNamesContributor> className, Project project) {
        return mySavingTime.containsKey(join(className, project));
    }

    public @Nullable Instant whenTrained(@NotNull Class<? extends VariableNamesContributor> className) {
        String str = mySavingTime.get(className.getSimpleName());
        return str == null ? null : Instant.parse(str);
    }

    public @Nullable Instant whenTrained(@NotNull Class<? extends VariableNamesContributor> className, Project project) {
        String str = mySavingTime.get(join(className, project));
        return str == null ? null : Instant.parse(str);
    }

    public boolean needRetraining(Class<? extends VariableNamesContributor> className, Project project) {
        @Nullable Instant saveTime = ModelSaveTimeService.getInstance().whenTrained(className, project);
        AppSettingsState settings = AppSettingsState.getInstance();
        Duration modelsLifetime = Duration.of(settings.modelsLifetime, settings.modelsLifetimeUnit);
        return settings.automaticTraining && !isTraining &&
                (saveTime == null || !Duration.between(saveTime, Instant.now()).minus(modelsLifetime).isNegative());
    }
}
