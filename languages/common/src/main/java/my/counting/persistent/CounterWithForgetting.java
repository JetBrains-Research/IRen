package my.counting.persistent;

import com.intellij.completion.ngram.slp.counting.Counter;
import my.counting.persistent.trie.CountersCache;
import my.counting.trie.MapTrieCounter;
import org.jetbrains.annotations.NotNull;

import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import java.util.stream.IntStream;

public class CounterWithForgetting extends PersistentCounter {
    private final PersistentCounter persistentCounter;
    private Counter counterToForget = new MapTrieCounter();

    @Override
    public void countBatch(List<List<Integer>> indices) {
        counterToForget = new MapTrieCounter();
    }

    @Override
    public void unCountBatch(List<List<Integer>> indices) {
        counterToForget.countBatch(indices);
    }

    @Override
    public void count(List<Integer> indices) {
        counterToForget = new MapTrieCounter();
    }

    @Override
    public void unCount(List<Integer> indices) {
        counterToForget.count(indices);
    }

    public CounterWithForgetting(PersistentCounter persistentCounter) {
        this.persistentCounter = persistentCounter;
    }

    /**
     * Don't use. Added for compatibility reasons.
     */
    public CounterWithForgetting() {
        this(null);
    }

    @Override
    public void prepareCache() {
        persistentCounter.prepareCache();
    }

    @Override
    public @NotNull CountersCache getCache() {
        return persistentCounter.getCache();
    }

    @Override
    public int getCount() {
        return persistentCounter.getCount() - counterToForget.getCount();
    }

    @Override
    public long[] getCounts(List<Integer> indices) {
        long[] counts = persistentCounter.getCounts(indices);
        long[] countsToSubtract = counterToForget.getCounts(indices);
        return subtractArrays(counts, countsToSubtract);
    }

    private long[] subtractArrays(long[] counts, long[] countsToSubtract) {
        final long[] res = new long[2];
        IntStream.range(0, 2).forEach(i -> res[i] = Math.max(0, counts[i] - countsToSubtract[i]));
        return res;
    }

    @Override
    public List<Integer> getTopSuccessors(List<Integer> indices, int limit) {
        return persistentCounter.getTopSuccessors(indices, limit);
    }

    @Override
    public void writeExternal(ObjectOutput out) {
    }

    @Override
    public void readExternal(ObjectInput in) {
    }
}
