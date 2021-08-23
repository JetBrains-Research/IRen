package org.jetbrains.id.names.suggesting.utils;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.id.names.suggesting.api.VariableNamesContributor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class StringUtils {
    public static final String STRING_TOKEN = "<str>";
    public static final String NUMBER_TOKEN = "<num>";
    public static final String VARIABLE_TOKEN = "<var>";
    public static final String END_SUBTOKEN = "<end>";
    public static final List<String> NumberTypes = Arrays.asList("INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL");
    public static final List<String> IntegersToLeave = Arrays.asList("0", "1", "32", "64");

    public static @NotNull Collection<String> subtokenSplit(@NotNull String token) {
        return Arrays.asList(token.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])|_"));
    }

    public static @NotNull String join(@NotNull Class<? extends VariableNamesContributor> className, @NotNull Project project) {
        return String.join("_", className.getSimpleName(), project.getLocationHash());
    }
}
