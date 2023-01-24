package com.intellij.completion.ngram.slp.counting.trie.my.persistent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class CountersCache {
    public static int CACHE_DEPTH = 1;
    public static int MAXIMUM_CACHE_SIZE = 3_000;
    private final String counterPath;

    public CountersCache(String counterPath) {
        this.counterPath = counterPath;
    }

    private final LoadingCache<Integer, Object> dynamicCache =
            CacheBuilder.newBuilder()
                    .maximumSize(MAXIMUM_CACHE_SIZE)
                    .build(new CacheLoader<>() {
                        @Override
                        public @NotNull Object load(@NotNull Integer key) throws IOException {
                            return readFromFile(key);
                        }
                    });


    public @Nullable Object get(int idx) {
        try {
            return dynamicCache.get(idx);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public @NotNull Object readFromFile(int idx) throws IOException {
        try (FileInputStream in = new FileInputStream(counterPath);
             DataInputStream din = new DataInputStream(new BufferedInputStream(in))) {
            changePosition(in, idx);
            int code = din.readInt();
            if (code < 0) {
                PersistentAbstractTrie value = new PersistentMapTrieCounter(counterPath, this);
                value.readExternal(din);
                return value;
            } else {
                return readArray(din, code);
            }
        }
    }

    public static int @NotNull [] readArray(@NotNull DataInputStream din, int length) throws IOException {
        int[] res = new int[length];
        for (int j = 0; j < length; j++) res[j] = din.readInt();
        return res;
    }

    public static void changePosition(FileInputStream in, int idx) throws IOException {
        FileChannel channel = in.getChannel();
        channel.position(idx);
    }
}
