package org.jetbrains.iren.services;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

public interface ModelsUsabilityService {
    static @NotNull ModelsUsabilityService getInstance() {
        return ApplicationManager.getApplication().getService(ModelsUsabilityService.class);
    }

    boolean isTraining();

    void setTraining(boolean training);

    void setUsable(@NotNull String name, boolean b);

    boolean isUsable(@NotNull String name);
}
