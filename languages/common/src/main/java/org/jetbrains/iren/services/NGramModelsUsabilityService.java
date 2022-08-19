package org.jetbrains.iren.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public interface NGramModelsUsabilityService {
    static @NotNull NGramModelsUsabilityService getInstance(@NotNull Project project) {
        return project.getService(NGramModelsUsabilityService.class);
    }

    boolean isTraining();

    void setTraining(boolean training);

    void setUsable(@NotNull String name, boolean b);

    boolean isUsable(@NotNull String name);
}
