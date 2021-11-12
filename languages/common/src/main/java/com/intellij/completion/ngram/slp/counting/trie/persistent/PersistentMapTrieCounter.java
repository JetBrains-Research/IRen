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

package com.intellij.completion.ngram.slp.counting.trie.persistent;

//import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
//import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
//import it.unimi.dsi.fastutil.ints.IntArrayList;
//import it.unimi.dsi.fastutil.ints.IntList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

public class PersistentMapTrieCounter extends PersistentAbstractTrie {
    private HashMap<Integer, Integer> map;

    public PersistentMapTrieCounter(String counterPath, CountersCache cache) {
        this(counterPath, cache, 1);
    }

    public PersistentMapTrieCounter(String counterPath, CountersCache cache, int initSize) {
        super(counterPath, cache);
        this.map = new LinkedHashMap<>(initSize);
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
        return map.keySet().stream().limit(end).collect(Collectors.toList());
    }

    @Override
    public @Nullable Object getSuccessor(int next) {
        @Nullable Integer idx = map.get(next);
        return idx == null ? null : readCounter(idx);
    }

    @Override
    public void readExternal(@NotNull RandomAccessFile raf, @NotNull Lock rafLock) throws IOException {
        int successors = raf.readInt();
        byte[] bs = new byte[(successors + 1) * 4 * 2];
        try {
            raf.readFully(bs);
        } finally {
            rafLock.unlock();
        }
        try (ByteArrayInputStream bin = new ByteArrayInputStream(bs);
             DataInputStream din = new DataInputStream(bin)) {
            this.counts = new int[2];
            this.counts[0] = din.readInt();
            this.counts[1] = din.readInt();
            this.map = new HashMap<>(successors, 0.9f);
            for (int pos = 0; pos < successors; pos++) {
                int key = din.readInt();
                int idx = din.readInt();
                map.put(key, idx);
            }
        }
    }

    @Override
    protected Collection<Integer> getSuccessorIdxs() {
        return map.values();
    }
}
