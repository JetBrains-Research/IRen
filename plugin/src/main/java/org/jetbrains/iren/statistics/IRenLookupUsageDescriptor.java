package org.jetbrains.iren.statistics;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor;
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor;
import com.intellij.internal.statistic.eventLog.events.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.config.ModelType;
import org.jetbrains.iren.rename.IRenLookups;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.iren.statistics.IRenCollectorExtension.irenModelType;
import static org.jetbrains.iren.statistics.IRenCollectorExtension.irenProbability;

@SuppressWarnings("UnstableApiUsage")
public class IRenLookupUsageDescriptor implements LookupUsageDescriptor {
    @Override
    public @NotNull String getExtensionKey() {
        return "iren";
    }

    @Override
    public List<EventPair<?>> getAdditionalUsageData(@NotNull LookupResultDescriptor lookupResultDescriptor) {
        LookupElement lookupElement = lookupResultDescriptor.getSelectedItem();
        List<EventPair<?>> eventPairs = new ArrayList<>();
        if (lookupElement == null) return eventPairs;
        @Nullable String model_type = lookupElement.getUserData(IRenLookups.modelTypeKey);
        if (model_type == null) return eventPairs;
        eventPairs.add(irenModelType.with(model_type));
        if (!model_type.equals(ModelType.DEFAULT.toString())) {
            @Nullable Double probability = lookupElement.getUserData(IRenLookups.LookupWithProbability.probabilityKey);
            if (probability == null) return eventPairs;
            eventPairs.add(irenProbability.with(probability));
        }
        return eventPairs;
    }
}
