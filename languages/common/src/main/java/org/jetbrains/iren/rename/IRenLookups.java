package org.jetbrains.iren.rename;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.config.ModelType;

import java.util.LinkedHashMap;
import java.util.Map;

public class IRenLookups {
    public static Key<String> modelTypeKey = new Key<>("model_type");

    public static class LookupWithProbability extends LookupElementDecorator<LookupElement> {
        public static Key<Double> probabilityKey = new Key<>("probability");

        protected LookupWithProbability(@NotNull LookupElement delegate, Map<String, Double> namesProbs, LinkedHashMap<String, ModelType> modelTypes) {
            super(delegate);
            String name = getLookupString();
            putUserData(modelTypeKey, modelTypes.get(name).toString());
            putUserData(probabilityKey, namesProbs.get(name));
        }

        @Override
        public void renderElement(LookupElementPresentation presentation) {
            super.renderElement(presentation);
            Double probability = getUserData(LookupWithProbability.probabilityKey);
            if (probability == null) return;
            String modelType = getUserData(modelTypeKey);
            presentation.setTypeText(String.format("%s%3.0f%%", modelType, probability * 100));
        }
    }

    public static class Default extends LookupElementDecorator<LookupElement> {
        protected Default(@NotNull LookupElement delegate) {
            super(delegate);
            putUserData(modelTypeKey, ModelType.DEFAULT.toString());
        }
    }
}
