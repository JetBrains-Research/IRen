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

import static org.jetbrains.iren.ModelManager.join;

@State(name = "ModelStatsService",
        storages = {@Storage("ModelSaveTime.xml")})
public class ModelStatsService implements PersistentStateComponent<ModelStatsService> {
    @XMap(propertyElementName = "saveTime", keyAttributeName = "model", valueAttributeName = "time")
    public final Map<String, String> mySavingTime = new HashMap<>();
    @Transient
    private final Set<String> myUsable = new HashSet<>();
    @Transient
    private boolean myTraining = false;

    @Override
    public @Nullable ModelStatsService getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull ModelStatsService state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public void setTraining(boolean training) {
        myTraining = training;
    }

    public boolean isTraining() {
        return myTraining;
    }

    public static @NotNull ModelStatsService getInstance() {
        return ServiceManager.getService(ModelStatsService.class);
    }

    public void setTrainedTime(@NotNull Class<? extends VariableNamesContributor> className) {
            mySavingTime.put(className.getSimpleName(), Instant.now().toString());
    }

    public void setTrainedTime(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
            mySavingTime.put(join(className, project), Instant.now().toString());
    }

    public boolean isSomethingLoaded() {
        return !myUsable.isEmpty();
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

    public void setUsable(@NotNull Class<? extends VariableNamesContributor> className, boolean b) {
        if (b) {
            myUsable.add(className.getSimpleName());
        } else {
            myUsable.remove(className.getSimpleName());
        }
    }

    public void setUsable(@NotNull Class<? extends VariableNamesContributor> className, Project project, boolean b) {
        if (b) {
            myUsable.add(join(className, project));
        } else {
            myUsable.remove(join(className, project));
        }
    }

    public boolean isUsable(@NotNull Class<? extends VariableNamesContributor> className) {
        return myUsable.contains(className.getSimpleName());
    }

    public boolean isUsable(Class<? extends VariableNamesContributor> className, Project project) {
        return myUsable.contains(join(className, project));
    }
}
