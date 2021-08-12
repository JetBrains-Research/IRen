package org.jetbrains.id.names.suggesting.storages;

import com.intellij.completion.ngram.slp.translating.Vocabulary;

import java.util.Collection;
import java.util.HashMap;

public class StringCounter extends HashMap<String, Integer> {
    @Override
    public Integer get(Object key) {
        return super.getOrDefault(key, 0);
    }

    @Override
    public Integer put(String key, Integer value) {
        return super.put(key, this.get(key) + value);
    }

    public Integer put(String key) {
        return put(key, 1);
    }

    public void putAll(Collection<String> collection) {
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
