package org.jetbrains.iren.rename;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementDecorator;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class MyLookup {
    public static Key<String> model_type = new Key<>("model_type");

    public static class NGram extends LookupElementDecorator<LookupElement> {
        public static Key<Double> probability = new Key<>("probability");
        public static String MODEL_TYPE = "ngram";

        protected NGram(@NotNull LookupElement delegate, Map<String, Double> namesProbs) {
            super(delegate);
            putUserData(model_type, MODEL_TYPE);
            putUserData(probability, namesProbs.get(getLookupString()));
        }

        @Override
        public void renderElement(LookupElementPresentation presentation) {
            super.renderElement(presentation);
            Double probability = getUserData(NGram.probability);
            if (probability == null) return;
            presentation.setTypeText(String.format("%.3f", probability));
        }

        @Override
        public void handleInsert(@NotNull InsertionContext context) {
            super.handleInsert(context);
//            TODO: add ConsistencyChecker.rememberRenamedVariable
        }
    }

    public static class Default extends LookupElementDecorator<LookupElement> {
        public static String MODEL_TYPE = "default";

        protected Default(@NotNull LookupElement delegate) {
            super(delegate);
            putUserData(model_type, MODEL_TYPE);
        }

        @Override
        public void handleInsert(@NotNull InsertionContext context) {
            super.handleInsert(context);
        }
    }
}
