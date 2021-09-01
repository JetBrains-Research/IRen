package org.jetbrains.iren;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.api.VariableNamesContributor;
import org.jetbrains.iren.settings.AppSettingsState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jetbrains.iren.utils.StringUtils.join;

@State(name = "ModelStatsService",
        storages = {@Storage("ModelSaveTime.xml")})
public class ModelStatsService implements PersistentStateComponent<ModelStatsService> {
    @Transient
    private boolean isTraining = false;
    @XMap(propertyElementName = "saveTime", keyAttributeName = "model", valueAttributeName = "time")
    public final Map<String, String> mySavingTime = new HashMap<>();
    @Transient
    private final Set<String> loaded = new HashSet<>();

    @Override
    public @Nullable ModelStatsService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ModelStatsService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void setTraining(boolean training) {
        isTraining = training;
    }

    public boolean isTraining() {
        return isTraining;
    }

    public static @NotNull ModelStatsService getInstance() {
        return ServiceManager.getService(ModelStatsService.class);
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
        return !loaded.isEmpty();
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
        @Nullable Instant saveTime = ModelStatsService.getInstance().whenTrained(className, project);
        AppSettingsState settings = AppSettingsState.getInstance();
        Duration modelsLifetime = Duration.of(settings.modelsLifetime, settings.modelsLifetimeUnit);
        return (saveTime == null || !Duration.between(saveTime, Instant.now()).minus(modelsLifetime).isNegative());
    }

    public void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, boolean b) {
        if (b) {
            loaded.add(className.getSimpleName());
        } else {
            loaded.remove(className.getSimpleName());
        }
    }

    public void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, Project project, boolean b) {
        if (b) {
            loaded.add(join(className, project));
        } else {
            loaded.remove(join(className, project));
        }
    }

    public boolean isLoaded(@NotNull Class<? extends VariableNamesContributor> className) {
        return loaded.contains(className.getSimpleName());
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className, Project project) {
        return loaded.contains(join(className, project));
    }
}
