package org.jetbrains.iren.utils;

import com.intellij.util.text.NameUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class StringUtils {
    public static final String STRING_TOKEN = "<str>";
    public static final String NUMBER_TOKEN = "<num>";
    public static final String VARIABLE_TOKEN = "<var>";
    public static final String END_SUBTOKEN = "<end>";
    public static final List<String> INTEGERS_TO_LEAVE = Arrays.asList("0", "1", "32", "64");
    public static final String NEW_LINE_TOKEN = "NEW_LINE";
    public static final String INDENT_TOKEN = "INDENT";
    public static final String DEDENT_TOKEN = "DEDENT";
    public static final String SPACE_TOKEN = "▁";
    public static final String STR_NEW_LINE_TOKEN = "STRNEWLINE";
    public static final String STR_TAB_TOKEN = "TABSYMBOL";

    public static final Set<String> INDENT_TOKENS = Set.of(NEW_LINE_TOKEN, INDENT_TOKEN, DEDENT_TOKEN);

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
}
