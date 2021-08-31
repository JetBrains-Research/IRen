package org.jetbrains.iren;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public class IdNamesSuggestingBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.IRen";
    private static final IdNamesSuggestingBundle INSTANCE = new IdNamesSuggestingBundle();

    private IdNamesSuggestingBundle() {
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
