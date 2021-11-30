package org.jetbrains.iren.services;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ModelsUsabilityService {
    private @Transient final Set<String> myUsable = new HashSet<>();
    private @Transient boolean training = false;

    public static @NotNull ModelsUsabilityService getInstance() {
        return ApplicationManager.getApplication().getService(ModelsUsabilityService.class);
    }

    public boolean isTraining() {
        return training;
    }

    public void setTraining(boolean training) {
        this.training = training;
    }

    public boolean isSomethingLoaded() {
        return !myUsable.isEmpty();
    }

    public void setUsable(@NotNull String name, boolean b) {
        if (b) {
            myUsable.add(name);
        } else {
            myUsable.remove(name);
        }
    }

    public boolean isUsable(@NotNull String name) {
        return myUsable.contains(name);
    }
}
