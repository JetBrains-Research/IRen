package org.jetbrains.iren.utils;

import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

public class StringUtils {
    public static final String STRING_TOKEN = "<str>";
    public static final String NUMBER_TOKEN = "<num>";
    public static final String VARIABLE_TOKEN = "<var>";
    public static final String END_SUBTOKEN = "<end>";
    public static final List<String> INTEGERS_TO_LEAVE = Arrays.asList("0", "1", "32", "64");

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

    public static boolean areSubtokensMatch(@Nullable String name, @NotNull Collection<String> suggestions) {
        return name != null && suggestions.stream().anyMatch(suggestion -> name.length() < 2 ?
                suggestion.equals(name) :
                checkAnySubtokensMatch(suggestion, name));
    }

    private static boolean checkAnySubtokensMatch(String first, String second) {
        @NotNull List<String> firstTokens = toLowerCasedTokens(first);
        @NotNull List<String> secondTokens = toLowerCasedTokens(second);
        Set<String> intersection = new HashSet<>(firstTokens); // use the copy constructor
        intersection.retainAll(new HashSet<>(secondTokens));
        return !intersection.isEmpty();
    }

    public static boolean areSubtokensMatch(@Nullable String name, @Nullable String suggestion) {
        return name != null && suggestion != null &&
                (name.length() < 2 ? suggestion.equals(name) : checkAnySubtokensMatch(suggestion, name));
    }

    /**
     * Copied from {@link String#contains}
     */
    private static boolean checkSubtokensMatch(String suggestion, String name) {
        @NotNull List<String> suggestionTokens = toLowerCasedTokens(suggestion);
        @NotNull List<String> nameTokens = toLowerCasedTokens(name);

        if (nameTokens.isEmpty()) return false;
        String first = nameTokens.get(0);
        int max = (suggestionTokens.size() - nameTokens.size());
        for (int i = 0; i <= max; i++) {
            // Look for first token.
            if (!Objects.equals(suggestionTokens.get(i), first)) {
                while (++i <= max && !Objects.equals(suggestionTokens.get(i), first)) ;
            }
            // Found the first token, now look at the rest of the suggestionTokens
            if (i <= max) {
                int j = i + 1;
                int end = j + nameTokens.size() - 1;
                for (int k = 1; j < end && Objects.equals(suggestionTokens.get(j), nameTokens.get(k)); j++, k++) ;
                if (j == end) {
                    // Found whole tokens.
                    return true;
                }
            }
        }
        return false;
    }
}
