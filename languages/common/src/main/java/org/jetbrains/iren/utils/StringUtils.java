package org.jetbrains.iren.utils;

import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

    public static boolean firstIsSuffixOfSecond(@Nullable String name1, @Nullable String name2) {
        if (name1 == null || name2 == null) return false;
        final List<String> tokens1 = toLowerCasedTokens(name1);
        final List<String> tokens2 = toLowerCasedTokens(name2);
        final int size1 = tokens1.size();
        final int size2 = tokens2.size();
        for (int i = 1; i <= size1; i++) {
            if (size2 < i || !tokens1.get(size1 - i).equals(tokens2.get(size2 - i))) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    public static List<String> toLowerCasedTokens(String name) {
        return Arrays.stream(NameUtilCore.splitNameIntoWords(name)).map(String::toLowerCase).collect(Collectors.toList());
    }

    public static boolean isSubstringOfSuggestions(@Nullable String name, @NotNull Collection<String> suggestions) {
        if (name == null) return false;
        String lcName = name.toLowerCase();
        return lcName.length() > 1 && suggestions.stream().anyMatch(suggestion -> suggestion.toLowerCase().contains(lcName));
    }

    public static boolean isSubstringOfSuggestion(@Nullable String name, @Nullable String suggestion) {
        if (name == null || suggestion == null) return false;
        String lcName = name.toLowerCase();
        return lcName.length() > 1 && suggestion.toLowerCase().contains(lcName);
    }
}
