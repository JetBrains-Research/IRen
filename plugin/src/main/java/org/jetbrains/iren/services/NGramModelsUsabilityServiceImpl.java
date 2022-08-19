package org.jetbrains.iren.services;

import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class NGramModelsUsabilityServiceImpl implements NGramModelsUsabilityService {
    private @Transient final Set<String> myUsable = new HashSet<>();
    private @Transient boolean training = false;

    @Override
    public boolean isTraining() {
        return training;
    }

    @Override
    public void setTraining(boolean training) {
        this.training = training;
    }

    @Override
    public void setUsable(@NotNull String name, boolean b) {
        if (b) {
            myUsable.add(name);
        } else {
            myUsable.remove(name);
        }
    }

    @Override
    public boolean isUsable(@NotNull String name) {
        return myUsable.contains(name);
    }
}
