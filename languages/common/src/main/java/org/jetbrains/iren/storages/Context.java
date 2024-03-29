package org.jetbrains.iren.storages;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Context is a snippet of code separated on tokens that contains all references of some variable.
 *
 * @param <T> class of the tokens (String or Integer)
 */
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

    public static class Statistics {
        public static final Statistics EMPTY = new Statistics(0, 0);
        public int usageNumber;
        public int countsSum;

        public Statistics(int usageNumber, int countsSum) {
            this.usageNumber = usageNumber;
            this.countsSum = countsSum;
        }

        public double countsMean() {
            return usageNumber > 0 ? countsSum / (double) usageNumber : 0.;
        }
    }

    public List<List<T>> splitByUsages() {
        List<List<T>> res = new ArrayList<>();
        int lastIdx = 0;
        for (int idx: varIdxs) {
            res.add(tokens.subList(lastIdx, idx));
            lastIdx = idx + 1;
        }
        res.add(tokens.subList(lastIdx, tokens.size()));
        return res;
    }

    // --------------------------- For tests ---------------------------
    @Override
    public String toString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static Context<String> deserialize(BufferedReader reader) throws IOException {
        Gson gson = new Gson();
        Type type = new TypeToken<Context<String>>(){}.getType();
        return gson.fromJson(reader, type);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Context
                && varIdxs.equals(((Context<?>) obj).varIdxs)
                && tokens.equals(((Context<?>) obj).tokens);
    }
}
