package org.jetbrains.iren.utils;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class RenameBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.Rename";
    private static final RenameBundle INSTANCE = new RenameBundle();

    private RenameBundle() {
        super(BUNDLE);
    }

    public static @NotNull String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                          Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }

    public static @NotNull Supplier<String> messagePointer(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                                           Object @NotNull ... params) {
        return INSTANCE.getLazyMessage(key, params);
    }


}
