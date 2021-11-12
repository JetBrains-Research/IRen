package com.intellij.completion.ngram.slp.counting.trie.persistent;

import com.intellij.completion.ngram.slp.counting.Counter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Counter which is dumped on disk. Its n-gram counts can't be changed.
 */
public abstract class PersistentCounter implements Counter {
    @Override
    public void countBatch(List<List<Integer>> indices) {
    }

    @Override
    public void unCountBatch(List<List<Integer>> indices) {
    }

    @Override
    public void count(List<Integer> indices) {
    }

    @Override
    public void unCount(List<Integer> indices) {
    }

    @Override
    public final int[] getDistinctCounts(int range, List<Integer> indices) {
        return new int[0];
    }

    @Override
    public final int getCountOfCount(int n, int count) {
        return 0;
    }

    @Override
    public final int getSuccessorCount(List<Integer> indices) {
        return 0;
    }

    @Override
    public final int getSuccessorCount() {
        return 0;
    }

    public abstract void prepareCache();

    public abstract @NotNull CountersCache getCache();
}
