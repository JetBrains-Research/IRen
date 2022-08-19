package org.jetbrains.iren.utils;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NotNull;

public class DOBFModelUtils extends ModelUtils {
    @Override
    public @NotNull String getModelsDirectoryName() {
        return "DOBF_models";
    }

    @Override
    public @NotNull String getVersion() {
        return "1";
    }

    public @NotNull String getName(@NotNull Language language) {
        return language.getDisplayName() + "_" + getVersion();
    }
}
