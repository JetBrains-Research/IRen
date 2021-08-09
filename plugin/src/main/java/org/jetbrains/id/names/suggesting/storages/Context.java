package org.jetbrains.id.names.suggesting.storages;

import java.util.ArrayList;
import java.util.List;

public class Context {
    final List<String> tokens;
    final List<Integer> varIdxs;

    public Context(List<String> tokens, List<Integer> varIdxs) {
        this.tokens = tokens;
        this.varIdxs = varIdxs;
    }

    public List<String> getTokens() {
        return tokens;
    }

    public List<Integer> getVarIdxs() {
        return varIdxs;
    }

    public Context with(String name) {
        List<String> newTokens = new ArrayList<>(tokens);
        for (int idx : varIdxs) {
            newTokens.set(idx, name);
        }
        return new Context(newTokens, varIdxs);
    }
}
