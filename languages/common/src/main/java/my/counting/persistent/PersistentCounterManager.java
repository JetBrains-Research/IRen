package my.counting.persistent;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.intellij.completion.ngram.slp.counting.Counter;
import com.intellij.openapi.util.Pair;
import my.counting.persistent.trie.PersistentAbstractTrie;
import my.counting.persistent.trie.PersistentArrayTrieCounter;
import my.counting.persistent.trie.PersistentMapTrieCounter;
import my.counting.trie.AbstractTrie;
import my.counting.trie.ArrayTrieCounter;
import my.counting.trie.MapTrieCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

public class PersistentCounterManager {
    public static int MAP_TRIE_COUNTER_CODE = -1;
    public static int ARRAY_TRIE_COUNTER_CODE = -2;
    private static final LoadingCache<Pair<String, Integer>, Object> cache =
            CacheBuilder.newBuilder()
                    .maximumSize(1000000)
                    .build(new CacheLoader<>() {
                        @Override
                        public Object load(@NotNull Pair<String, Integer> key) {
                            return readFromFile(key.getFirst(), key.getSecond());
                        }
                    });

    public static void serialize(String counterPath, Counter counter) {
//        TODO: change RandomAccessFile to something else
        try (RandomAccessFile raf = new RandomAccessFile(counterPath, "rw")) {
            int idx = write(counter, raf);
            raf.writeInt(idx);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static int write(Object counter, RandomAccessFile raf) throws IOException {
        if (counter instanceof MapTrieCounter) {
            return writeMapTrieCounter((MapTrieCounter) counter, raf);
        } else if (counter instanceof ArrayTrieCounter) {
            return writeArrayTrieCounter((ArrayTrieCounter) counter, raf);
        } else {
            return writeArray((int[]) counter, raf);
        }
    }

    private static int writeMapTrieCounter(@NotNull MapTrieCounter counter, RandomAccessFile raf) throws IOException {
//        Starting to write from children
        Map<Integer, Integer> childrenToWrite = new LinkedHashMap<>();
        for (Entry<Integer, Object> entry : counter.map.entrySet()) {
            if (entry.getValue() != null)
                childrenToWrite.put(entry.getKey(), write(entry.getValue(), raf));
        }
//        I hope that size of a counter won't be greater than 2 GB
        final int start = (int) raf.getFilePointer();
        raf.writeInt(MAP_TRIE_COUNTER_CODE);
        writeAfterChildren(counter, raf, childrenToWrite);
        return start;
    }

    private static int writeArrayTrieCounter(@NotNull ArrayTrieCounter counter, RandomAccessFile raf) throws IOException {
//        Starting to write from children
        Map<Integer, Integer> childrenToWrite = new LinkedHashMap<>();
        for (int i = 0; i < counter.indices.length; i++) {
            if (counter.indices[i] < Integer.MAX_VALUE && counter.successors[i] != null)
                childrenToWrite.put(counter.indices[i], write(counter.successors[i], raf));
        }
//        I hope that size of a counter won't be greater than 2 GB
        final int start = (int) raf.getFilePointer();
        raf.writeInt(ARRAY_TRIE_COUNTER_CODE);
        writeAfterChildren(counter, raf, childrenToWrite);
        return start;
    }

    private static void writeAfterChildren(@NotNull AbstractTrie counter,
                                           @NotNull RandomAccessFile raf,
                                           Map<Integer, Integer> childrenToWrite) throws IOException {
        raf.writeInt(counter.counts[0]);
        raf.writeInt(counter.counts[1]);
        raf.writeInt(childrenToWrite.size());
        for (Entry<Integer, Integer> entry : childrenToWrite.entrySet()) {
            raf.writeInt(entry.getKey());
            raf.writeInt(entry.getValue());
        }
    }

    public static int writeArray(int @NotNull [] array, @NotNull RandomAccessFile raf) throws IOException {
//        I hope that size of a counter won't be greater than 2 GB
        final int start = (int) raf.getFilePointer();
        raf.writeInt(array.length);
        for (int i : array) raf.writeInt(i);
        return start;
    }

    public static int @NotNull [] readArray(RandomAccessFile raf, int length) throws IOException {
        int[] res = new int[length];
        for (int j = 0; j < length; j++) res[j] = raf.readInt();
        return res;
    }

    public static @Nullable Counter deserialize(String counterPath) {
        int idx;
        try (RandomAccessFile raf = new RandomAccessFile(counterPath, "r")) {
//            Two integers from the end defines root counter
            raf.seek(raf.length() - 4);
            idx = raf.readInt();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        final Object counter = readFromFile(counterPath, idx);
        return counter instanceof Counter ? (Counter) counter : null;
    }

    public static @Nullable Object readCounter(String counterPath, int idx) {
        Pair<String, Integer> nameIdx = Pair.create(counterPath, idx);
        try {
//            TODO: make Cache (static for mapTries and dynamic for others) and store one instance in each node of the counter. On loading cache is preparing.
            return cache.get(nameIdx);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    private static Object readFromFile(String counterPath, int idx) {
        try (RandomAccessFile raf = new RandomAccessFile(counterPath, "r")) {
            raf.seek(idx);
            int code = raf.readInt();
            if (code < 0) {
                PersistentAbstractTrie value;
                if (code == PersistentCounterManager.ARRAY_TRIE_COUNTER_CODE)
                    value = new PersistentArrayTrieCounter(counterPath);
                else value = new PersistentMapTrieCounter(counterPath);
                value.readExternal(raf);
                return value;
            } else {
                return readArray(raf, code);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
