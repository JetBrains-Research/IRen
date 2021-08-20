package org.jetbrains.id.names.suggesting;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;
import org.jetbrains.id.names.suggesting.settings.AppSettingsState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.id.names.suggesting.utils.StringUtils.join;

public class LoadingTimeService implements Disposable {
    private final Map<String, Instant> myLoadingTime = new HashMap<>();
    private boolean isTraining = false;
    private boolean settingsChanged = false;


    public void setTraining(boolean training) {
        isTraining = training;
    }


    public static @NotNull LoadingTimeService getInstance() {
        return ServiceManager.getService(LoadingTimeService.class);
    }

    public void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, boolean b) {
        if (b) {
            myLoadingTime.put(className.getName(), Instant.now());
        } else {
            myLoadingTime.remove(className.getName());
        }
    }

    public void setLoaded(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project, boolean b) {
        if (b) {
            myLoadingTime.put(join(className, project), Instant.now());
        } else {
            myLoadingTime.remove(join(className, project));
        }
    }

    public boolean isSomethingLoaded() {
        return !myLoadingTime.isEmpty();
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className) {
        return myLoadingTime.containsKey(className.getName());
    }

    public boolean isLoaded(Class<? extends VariableNamesContributor> className, Project project) {
        return myLoadingTime.containsKey(join(className, project));
    }

    public @Nullable Instant whenLoaded(@NotNull Class<? extends VariableNamesContributor> className) {
        return myLoadingTime.get(className.getName());
    }

    public @Nullable Instant whenLoaded(@NotNull Class<? extends VariableNamesContributor> className, Project project) {
        return myLoadingTime.get(join(className, project));
    }

    public boolean needRetraining(Class<? extends VariableNamesContributor> className, Project project) {
        @Nullable Instant loadingTime = LoadingTimeService.getInstance().whenLoaded(className, project);
        AppSettingsState settings = AppSettingsState.getInstance();
        Duration modelsLifetime = Duration.of(settings.modelsLifetime, settings.modelsLifetimeUnit);
        boolean result = settings.automaticTraining && !isTraining &&
                (settingsChanged || loadingTime == null || !Duration.between(loadingTime, Instant.now()).minus(modelsLifetime).isNegative());
        settingsChanged = false;
        return result;
    }

    @Override
    public void dispose() {
        myLoadingTime.clear();
    }

    public void settingsChanged() {
        settingsChanged = true;
    }
}
