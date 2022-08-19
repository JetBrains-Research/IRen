package org.jetbrains.iren.config;

import java.util.Set;

public class InferenceStrategies {
    public static final Set<ModelType> ALL = Set.of(ModelType.DEFAULT, ModelType.NGRAM, ModelType.DOBF);
    public static final Set<ModelType> FAST_WITHOUT_DEFAULT = Set.of(ModelType.NGRAM);
    public static final Set<ModelType> SLOW_WITHOUT_DEFAULT = Set.of(ModelType.DOBF);
    public static final Set<ModelType> DEFAULT_ONLY = Set.of(ModelType.DEFAULT);
    public static final Set<ModelType> WITHOUT_DEFAULT = Set.of(ModelType.DOBF, ModelType.NGRAM);
}
