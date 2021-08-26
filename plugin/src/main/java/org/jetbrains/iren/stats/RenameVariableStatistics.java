package org.jetbrains.iren.stats;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(name = "RenameVariableStatistics",
        storages = {@Storage("RenameVariableStatistics.xml")})
public class RenameVariableStatistics implements PersistentStateComponent<RenameVariableStatistics> {
    public int total = 0;
    public int applied = 0;
    public List<Integer> ranks = new ArrayList<>();

    public static RenameVariableStatistics getInstance() {
        return ApplicationManager.getApplication().getService(RenameVariableStatistics.class);
    }

    @Override
    public @Nullable RenameVariableStatistics getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull RenameVariableStatistics state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
