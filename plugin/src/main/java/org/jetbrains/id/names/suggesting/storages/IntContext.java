package org.jetbrains.id.names.suggesting.storages;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class IntContext {
    final List<Integer> tokens;
    final List<Integer> varIdxs;

    public List<Integer> getTokens() {
        return tokens;
    }

    public List<Integer> getVarIdxs() {
        return varIdxs;
    }

    public IntContext(List<Integer> tokens, List<Integer> varIdxs) {
        this.tokens = tokens;
        this.varIdxs = varIdxs;
    }

    public @NotNull IntContext with(int tokenIdx) {
        List<Integer> newTokens = new ArrayList<>(tokens);
        for (int idx : varIdxs) {
            newTokens.set(idx, tokenIdx);
        }
        return new IntContext(newTokens, varIdxs);
    }

    public static @NotNull IntContext fromContext(@NotNull Context context, @NotNull Vocabulary vocabulary) {
        return new IntContext(vocabulary.toIndices(context.getTokens()), context.getVarIdxs());
    }
}
