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

package my.counting.persistent.trie;

//import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
//import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
//import it.unimi.dsi.fastutil.ints.IntArrayList;
//import it.unimi.dsi.fastutil.ints.IntList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static my.counting.persistent.PersistentCounterManager.readCounter;

public class PersistentMapTrieCounter extends PersistentAbstractTrie {
    private HashMap<Integer, Integer> map;
    private ArrayList<Integer> pseudoOrdering;

    public PersistentMapTrieCounter(String counterPath) {
        this(counterPath, 1);
    }

    public PersistentMapTrieCounter(String counterPath, int initSize) {
        super(counterPath);
        //this.map = new Int2ObjectOpenHashMap<>(initSize);
        this.map = new HashMap<>(initSize);
        //this.map.defaultReturnValue(null);
        this.pseudoOrdering = new ArrayList<>();
    }

    private static final Map<Integer, Integer> cache = new HashMap<>();

    public PersistentMapTrieCounter() {
//		Added for compatibility reasons
        super("");
    }

    @Override
    public List<Integer> getTopSuccessorsInternal(int limit) {
        int classKey = this.hashCode();
        int countsKey = this.keyCode();
        Integer cached = cache.get(classKey);
        if (cached == null || cached != countsKey) {
            this.pseudoOrdering.sort(this::compareCounts);
        }
        int end = Math.min(this.pseudoOrdering.size(), limit);
        List<Integer> topSuccessors = new ArrayList<>(this.pseudoOrdering.subList(0, end));
        if (this.getSuccessorCount() > 10) cache.put(classKey, countsKey);
        return topSuccessors;
    }

    private int keyCode() {
        return 31 * (this.getSuccessorCount() + 31 * this.getCount());
    }

    @Override
    public @Nullable Object getSuccessor(int next) {
        @Nullable Integer idx = map.get(next);
        return idx == null ? null : readCounter(counterPath, idx);
    }

    private int compareCounts(Integer i1, Integer i2) {
        int base = -Integer.compare(getCount(getSuccessor(i1)), getCount(getSuccessor(i2)));
        if (base != 0) return base;
        return Integer.compare(i1, i2);
    }

    @Override
    public void readExternal(@NotNull RandomAccessFile raf) throws IOException {
        this.counts = new int[2];
        this.counts[0] = raf.readInt();
        this.counts[1] = raf.readInt();
        int successors = raf.readInt();
        this.map = new HashMap<>(successors, 0.9f);
        for (int pos = 0; pos < successors; pos++) {
            int key = raf.readInt();
            int idx = raf.readInt();
            map.put(key, idx);
            pseudoOrdering.add(key);
        }
    }
}
