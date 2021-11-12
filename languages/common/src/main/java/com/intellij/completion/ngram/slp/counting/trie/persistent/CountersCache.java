package com.intellij.completion.ngram.slp.counting.trie.persistent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CountersCache {
    public static int CACHE_DEPTH = 2;
    private final String counterPath;
    private RandomAccessFile raf = null;
    private final Lock rafLock = new ReentrantLock();

    public CountersCache(String counterPath) {
        this.counterPath = counterPath;
    }

    private final Map<Integer, Object> staticCache = new HashMap<>();
    private final LoadingCache<Integer, Object> dynamicCache =
            CacheBuilder.newBuilder()
                    .maximumSize(100_000_000)
                    .build(new CacheLoader<>() {
                        @Override
                        public @NotNull Object load(@NotNull Integer key) throws IOException {
                            return readFromFile(key);
                        }
                    });


    public @Nullable Object get(int idx) throws ExecutionException {
        if (staticCache.containsKey(idx)) {
            return staticCache.get(idx);
        }
        return dynamicCache.get(idx);
    }

    public void addToStatic(int idx, Object o) {
        staticCache.put(idx, o);
    }

    public @NotNull Object readFromFile(int idx) throws IOException {
        assert raf != null;
        rafLock.lock();
        raf.seek(idx);
        int code = raf.readInt();
        if (code < 0) {
//            For some reason PersistentArrayTrieCounter works buggy :(
            PersistentAbstractTrie value = new PersistentMapTrieCounter(counterPath, this);
//            if (code == PersistentCounterManager.ARRAY_TRIE_COUNTER_CODE)
//                value = new PersistentArrayTrieCounter(counterPath, this);
//            else value = new PersistentMapTrieCounter(counterPath, this);
            value.readExternal(raf, rafLock);
            return value;
        } else {
            return readArray(raf, code, rafLock);
        }
    }

    public static int @NotNull [] readArray(@NotNull RandomAccessFile raf, int length, @NotNull Lock rafLock) throws IOException {
        byte[] bs = new byte[length * 4];
        try {
            raf.readFully(bs);
        } catch (EOFException e) {
            System.out.printf("EOF: tried to read %d integers\n", length);
        } finally {
            rafLock.unlock();
        }
        int[] res = new int[length];
        try (ByteArrayInputStream bin = new ByteArrayInputStream(bs);
             DataInputStream din = new DataInputStream(bin)) {
            for (int j = 0; j < length; j++) res[j] = din.readInt();
        }
        return res;
    }

    public void openRaf() {
        if (raf != null) return;
        try {
            raf = new RandomAccessFile(counterPath, "r");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void closeRaf() {
        if (raf == null) return;
        try {
            raf.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        raf = null;
    }
}
