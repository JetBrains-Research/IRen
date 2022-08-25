package org.jetbrains.iren.config;

import java.util.Set;

public class InferenceStrategies {
    public static final Set<ModelType> ALL = Set.of(ModelType.DEFAULT, ModelType.NGRAM, ModelType.DOBF);
    public static final Set<ModelType> NGRAM_ONLY = Set.of(ModelType.NGRAM);
    public static final Set<ModelType> DOBF_ONLY = Set.of(ModelType.DOBF);
}
