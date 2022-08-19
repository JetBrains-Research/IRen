package org.jetbrains.iren.config;

public enum ModelType {
    DEFAULT("default"), NGRAM("ngram"), DOBF("dobf"), BOTH("both");

    private final String text;

    ModelType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
