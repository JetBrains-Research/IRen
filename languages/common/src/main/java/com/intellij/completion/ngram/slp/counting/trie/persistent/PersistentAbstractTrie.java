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

import com.intellij.completion.ngram.slp.counting.trie.ArrayStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;

public abstract class PersistentAbstractTrie extends PersistentCounter {
    int[] counts;
    protected String counterPath;

    @Override
    public @NotNull CountersCache getCache() {
        return cache;
    }

    CountersCache cache;

    public PersistentAbstractTrie(String counterPath, CountersCache cache) {
        this.counterPath = counterPath;
        this.counts = new int[2];
        this.cache = cache;
    }

    /**
     * Return a new PersistentAbstractTrie instance of your choosing.
     * For instance, {@link PersistentMapTrieCounter} at present returns a map for the root and second level, than a regular Trie,
     * whereas TrieCounter always uses a Trie.
     */
    public abstract Object getSuccessor(int key);

    protected abstract Collection<Integer> getSuccessorIdxs();

    abstract List<Integer> getTopSuccessorsInternal(int limit);

    public final void readExternal(ObjectInput in) {
    }

    public final void writeExternal(ObjectOutput out) {
    }

    public abstract void readExternal(@NotNull RandomAccessFile raf, @NotNull Lock rafLock) throws IOException;

    @Override
    public final int getCount() {
        return this.counts[0];
    }

    final int getCount(Object successor) {
        if (successor == null) return 0;
        else if (successor instanceof PersistentAbstractTrie) return ((PersistentAbstractTrie) successor).getCount();
        else return ((int[]) successor)[0];
    }

    public final int getContextCount() {
        return this.counts[1];
    }

    @Override
    public final long[] getCounts(List<Integer> indices) {
        if (indices.isEmpty()) return new long[]{getCount(), getCount()};
        return getCounts(indices, 0);
    }

    private long[] getCounts(List<Integer> indices, int index) {
        Integer next = indices.get(index);
        Object succ = this.getSuccessor(next);
        boolean nearLast = index == indices.size() - 1;
        // Recurse if applicable
        if ((succ instanceof PersistentAbstractTrie)) {
            PersistentAbstractTrie successor = (PersistentAbstractTrie) succ;
            if (!nearLast) return successor.getCounts(indices, index + 1);
            else return new long[]{successor.getCount(), this.counts[1]};
        }
        // Else, return counts from array if present
        long[] counts = new long[2];
        if (nearLast) counts[1] = this.counts[1];
        if (succ != null) {
            int[] successor = (int[]) succ;
            if (ArrayStorage.checkPartialSequence(indices, index, successor)) {
                counts[0] = successor[0];
                if (!nearLast) counts[1] = counts[0];
            } else if (!nearLast && successor.length >= indices.size() - index
                    && ArrayStorage.checkPartialSequence(indices.subList(0, indices.size() - 1), index, successor)) {
                counts[1] = successor[0];
            }
        }
        return counts;
    }

    private Object getSuccessorNode(List<Integer> indices, int index) {
        if (index == indices.size()) return this;
        int next = indices.get(index);
        Object succ = getSuccessor(next);
        if (succ == null) return null;
        else if (succ instanceof PersistentAbstractTrie) {
            PersistentAbstractTrie successor = (PersistentAbstractTrie) succ;
            return successor.getSuccessorNode(indices, index + 1);
        } else {
            int[] successor = (int[]) succ;
            if (!ArrayStorage.checkPartialSequence(indices, index, successor)) return null;
            int[] trueSucc = new int[1 + successor.length - (indices.size() - index)];
            trueSucc[0] = successor[0];
            for (int i = 1; i < trueSucc.length; i++) {
                trueSucc[i] = successor[i + indices.size() - index - 1];
            }
            return trueSucc;
        }
    }

    @Override
    public List<Integer> getTopSuccessors(List<Integer> indices, int limit) {
        Object successor = getSuccessorNode(indices, 0);
        if (successor == null) return new ArrayList<>();
        else if (successor instanceof PersistentAbstractTrie)
            return ((PersistentAbstractTrie) successor).getTopSuccessorsInternal(limit);
        else {
            int[] succ = (int[]) successor;
            List<Integer> successors = new ArrayList<>();
            if (succ.length > 1) successors.add(succ[1]);
            return successors;
        }
    }

    /**
     * Read counter from the file specified in cache.
     *
     * @param idx position of the counter in the file.
     * @return new or cached instance of the counter.
     */
    public @Nullable Object readCounter(int idx) {
        assert idx < Integer.MAX_VALUE;
        try {
            return cache.get(idx);
        } catch (ExecutionException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void prepareCache() {
        cache.openRaf();
        try {
            prepareCache(0);
        } finally {
            cache.closeRaf();
        }
    }

    private void prepareCache(int currentDepth) {
        if (currentDepth < CountersCache.CACHE_DEPTH) {
            ConcurrentHashMap<Integer, Object> successors = new ConcurrentHashMap<>();
            getSuccessorIdxs().forEach(idx -> {
                if (idx < Integer.MAX_VALUE) {
                    try {
                        successors.put(idx, cache.readFromFile(idx));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            successors.forEach((idx, counter) -> {
                if (counter != null) {
                    cache.addToStatic(idx, counter);
                    if (counter instanceof PersistentAbstractTrie) {
                        ((PersistentAbstractTrie) counter).prepareCache(currentDepth + 1);
                    }
                }
            });
        }
    }
}

