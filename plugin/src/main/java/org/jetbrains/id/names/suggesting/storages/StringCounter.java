package org.jetbrains.id.names.suggesting.storages;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
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

    public Vocabulary toVocabulary(int vocabularyCutOff) {
        Vocabulary vocab = new Vocabulary();
        this.forEach((k, v) -> {
            if (v >= vocabularyCutOff) {
                vocab.store(k, v);
            }
        });
        return vocab;
    }
}
