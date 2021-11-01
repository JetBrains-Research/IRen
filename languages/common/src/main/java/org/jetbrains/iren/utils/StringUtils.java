package org.jetbrains.iren.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class StringUtils {
    public static final String STRING_TOKEN = "<str>";
    public static final String NUMBER_TOKEN = "<num>";
    public static final String VARIABLE_TOKEN = "<var>";
    public static final String END_SUBTOKEN = "<end>";
    public static final List<String> IntegersToLeave = Arrays.asList("0", "1", "32", "64");

    public static @NotNull Collection<String> subtokenSplit(@NotNull String token) {
        return Arrays.asList(token.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])|_"));
    }

    public static @NotNull @Unmodifiable Collection<String> splitVariableType(@NotNull String type) {
        return List.of(type.replaceAll("(?<=\\W)(?=\\W)|(?<=\\w)(?=\\W)|(?<=\\W)(?=\\w)", " ").split("\\s+"));
    }
}
