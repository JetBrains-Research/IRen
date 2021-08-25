package org.jetbrains.iren.storages;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class Context<T> {
    final List<T> tokens;
    final List<Integer> varIdxs;

    public Context(List<T> tokens, List<Integer> varIdxs) {
        this.tokens = tokens;
        this.varIdxs = varIdxs;
    }

    public List<T> getTokens() {
        return tokens;
    }

    public List<Integer> getVarIdxs() {
        return varIdxs;
    }

    public Context<T> with(T name) {
        List<T> newTokens = new ArrayList<>(tokens);
        for (int idx : varIdxs) {
            newTokens.set(idx, name);
        }
        return new Context<>(newTokens, varIdxs);
    }

    public static @NotNull Context<Integer> fromStringToInt(@NotNull Context<String> context, @NotNull Vocabulary vocabulary) {
        return new Context<>(vocabulary.toIndices(context.getTokens()), context.getVarIdxs());
    }
}
