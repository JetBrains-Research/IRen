/* MIT License

 Copyright (c) 2018 SLP-team

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.
 */

package com.intellij.completion.ngram.slp.counting.trie.my.persistent;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PersistentMapTrieCounter extends PersistentAbstractTrie {
    private Int2IntOpenHashMap map;

    public PersistentMapTrieCounter(String counterPath, CountersCache cache) {
        this(counterPath, cache, 1);
    }

    public PersistentMapTrieCounter(String counterPath, CountersCache cache, int initSize) {
        super(counterPath, cache);
        map = new Int2IntOpenHashMap(initSize);
        map.trim();
    }

    /**
     * Don't use. Added for compatibility reasons.
     */
    public PersistentMapTrieCounter() {
        super("", null);
    }

    @Override
    public List<Integer> getTopSuccessorsInternal(int limit) {
        int end = Math.min(map.size(), limit);
        return map.keySet().intStream().limit(end).boxed().collect(Collectors.toList());
    }

    @Override
    public @Nullable Object getSuccessor(int next) {
        int idx = map.get(next);
        return idx < 0 ? null : readCounter(idx);
    }

    @Override
    public void readExternal(@NotNull DataInputStream din) throws IOException {
        int successors = din.readInt();
        this.counts = new int[2];
        this.counts[0] = din.readInt();
        this.counts[1] = din.readInt();
        this.map = new Int2IntOpenHashMap(successors);
        for (int pos = 0; pos < successors; pos++) {
            int key = din.readInt();
            int idx = din.readInt();
            map.put(key, idx);
        }
    }

    @Override
    protected Collection<Integer> getSuccessorIdxs() {
        return map.values();
    }
}
