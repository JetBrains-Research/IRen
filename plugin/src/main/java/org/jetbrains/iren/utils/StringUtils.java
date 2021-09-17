package org.jetbrains.iren.utils;

import org.jetbrains.annotations.NotNull;

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
}
