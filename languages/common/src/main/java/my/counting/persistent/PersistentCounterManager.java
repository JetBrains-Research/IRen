package my.counting.persistent;

import com.intellij.completion.ngram.slp.counting.Counter;
import my.counting.persistent.trie.CountersCache;
import my.counting.trie.AbstractTrie;
import my.counting.trie.ArrayTrieCounter;
import my.counting.trie.MapTrieCounter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class PersistentCounterManager {
    public static int MAP_TRIE_COUNTER_CODE = -1;
    public static int ARRAY_TRIE_COUNTER_CODE = -2;
    private final String counterPath;
    private DataOutputStream out;

    public PersistentCounterManager(String counterPath) {
        this.counterPath = counterPath;
    }

    public static void serialize(String counterPath, Counter counter) {
        new PersistentCounterManager(counterPath).serialize(counter);
    }

    public void serialize(Counter counter) {
        try (FileOutputStream fout = new FileOutputStream(counterPath);
             BufferedOutputStream bout = new BufferedOutputStream(fout)) {
            out = new DataOutputStream(bout);
            try {
                final int idx = write(counter);
                writeInt(idx);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int write(@NotNull Object counter) throws IOException {
        if (counter instanceof MapTrieCounter) {
            return writeMapTrieCounter((MapTrieCounter) counter);
        } else if (counter instanceof ArrayTrieCounter) {
            return writeArrayTrieCounter((ArrayTrieCounter) counter);
        } else {
            return writeArray((int[]) counter);
        }
    }

    private int writeMapTrieCounter(@NotNull MapTrieCounter counter) throws IOException {
//        Starting to write from children
        Map<Integer, Integer> childrenToWrite = new LinkedHashMap<>();
        for (Entry<Integer, Object> entry : counter.map.entrySet()) {
            if (entry.getValue() != null) {
                childrenToWrite.put(entry.getKey(), write(entry.getValue()));
            }
        }
//        I hope that size of a counter won't be greater than 2 GB
        final int start = out.size();
        writeInt(MAP_TRIE_COUNTER_CODE);
        writeAfterChildren(counter, childrenToWrite);
        return start;
    }

    private int writeArrayTrieCounter(@NotNull ArrayTrieCounter counter) throws IOException {
//        Starting to write from children
        Map<Integer, Integer> childrenToWrite = new LinkedHashMap<>();
        for (int i = 0; i < counter.indices.length; i++) {
            if (counter.indices[i] < Integer.MAX_VALUE &&
                    counter.successors[i] != null)
                childrenToWrite.put(counter.indices[i], write(counter.successors[i]));
        }
//        I hope that size of a counter won't be greater than 2 GB
        final int start = out.size();
        writeInt(ARRAY_TRIE_COUNTER_CODE);
        writeAfterChildren(counter, childrenToWrite);
        return start;
    }

    private void writeAfterChildren(@NotNull AbstractTrie counter,
                                    Map<Integer, Integer> childrenToWrite) throws IOException {
        writeInt(childrenToWrite.size());
        writeInt(counter.counts[0]);
        writeInt(counter.counts[1]);
        for (Entry<Integer, Integer> entry : childrenToWrite.entrySet()) {
            writeInt(entry.getKey());
            writeInt(entry.getValue());
        }
    }

    public int writeArray(int @NotNull [] array) throws IOException {
//        I hope that size of a counter won't be greater than 2 GB
        final int start = out.size();
        writeInt(array.length);
        for (int i : array) writeInt(i);
        return start;
    }

    private void writeInt(int i) throws IOException {
        out.writeInt(i);
    }

    public static @Nullable PersistentCounter deserialize(String counterPath) {
        try (RandomAccessFile raf = new RandomAccessFile(counterPath, "r")) {
//            Integer from the end defines root counter
            raf.seek(raf.length() - 4);
            int idx = raf.readInt();
            final CountersCache cache = new CountersCache(counterPath);
            cache.openRaf();
            final Object counter = cache.readFromFile(idx);
            if (counter instanceof PersistentCounter) {
                ((PersistentCounter) counter).prepareCache();
                return new CounterWithForgetting((PersistentCounter) counter);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }
}
