package org.jetbrains.iren.storages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class StringCounter extends ConcurrentHashMap<String, Integer> {
    @Override
    public Integer get(Object key) {
        if (super.get(key) == null) {
            return 0;
        }
        return super.get(key);
    }

    @Override
    public Integer put(@NotNull String key, @NotNull Integer value) {
        return super.put(key, this.get(key) + value);
    }

    public Integer put(String key) {
        return put(key, 1);
    }

    public void putAll(@Nullable Collection<String> collection) {
        if (collection == null) return;
        collection.forEach(this::put);
    }

    public void toVocabulary(@NotNull Vocabulary vocabulary, int cutOff) {
        this.forEach((k, v) -> {
            if (v >= cutOff) {
                vocabulary.store(k, v);
            }
        });
        vocabulary.close();
    }
}
