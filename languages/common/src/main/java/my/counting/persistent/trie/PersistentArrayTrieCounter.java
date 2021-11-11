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

import com.intellij.completion.ngram.slp.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PersistentArrayTrieCounter extends PersistentAbstractTrie {

    public int[] indices;
    public int[] successorIdxs;

    public PersistentArrayTrieCounter(String counterPath, CountersCache cache) {
        this(counterPath, cache, 1);
    }

    public PersistentArrayTrieCounter(String counterPath, CountersCache cache, int initSize) {
        super(counterPath, cache);
        this.indices = new int[initSize];
        this.successorIdxs = new int[initSize];
        Arrays.fill(this.indices, Integer.MAX_VALUE);
    }

    /**
     * Don't use. Added for compatibility reasons.
     */
    public PersistentArrayTrieCounter() {
        super("", null);
    }

    @Override
    public List<Integer> getTopSuccessorsInternal(int limit) {
        return IntStream.range(0, this.indices.length)
                .filter(i -> this.indices[i] != Integer.MAX_VALUE)
                .mapToObj(i -> Pair.of(this.indices[i], this.getCount(this.readSuccessor(i))))
                .filter(p -> p.right != null && p.right > 0)
                .sorted((p1, p2) -> -Integer.compare(p1.right, p2.right))
                .limit(limit)
                .map(p -> p.left)
                .collect(Collectors.toList());
    }

    public Object getSuccessor(int key) {
        int ix = getSuccIx(key);
        if (ix < 0) {
            return null;
        } else return this.readSuccessor(ix);
    }

    @Override
    public Collection<Integer> getSuccessorIdxs() {
        Collection<Integer> res = new ArrayList<>();
        for (int idx : successorIdxs)
            if (idx < Integer.MAX_VALUE) {
                res.add(idx);
            }
        return res;
    }

    private Object readSuccessor(int ix) {
        return readCounter(successorIdxs[ix]);
    }

    /*
     * Map bookkeeping
     */
    private int getSuccIx(int key) {
        // Quickly check if key is stored at its purely sequential location; the 'root' trie is usually
        // populated with the whole vocabulary in order up to some point, so a quick guess can save time.
        if (this.indices.length > 1000 && key > 0 && key <= this.indices.length && this.indices[key - 1] == key)
            return key - 1;
        // Otherwise, binary search will do
        return Arrays.binarySearch(this.indices, key);
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
            this.counts[0] = raf.readInt();
            this.counts[1] = raf.readInt();
            this.indices = new int[successors + 1];
            this.successorIdxs = new int[successors + 1];
            this.indices[successors] = Integer.MAX_VALUE;
            for (int pos = 0; pos < successors; pos++) {
                this.indices[pos] = din.readInt();
                this.successorIdxs[pos] = din.readInt();
            }
        }
    }
}
