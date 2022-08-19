package org.jetbrains.iren.statistics;

import com.intellij.codeInsight.lookup.impl.LookupUsageTracker;
import com.intellij.internal.statistic.eventLog.events.DoubleEventField;
import com.intellij.internal.statistic.eventLog.events.EventField;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.StringEventField;
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.iren.config.ModelType;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class IRenCollectorExtension implements FeatureUsageCollectorExtension {
    public final static StringEventField irenModelType = EventFields.String("iren_model_type",
            Arrays.asList(ModelType.DEFAULT.toString(), "both", ModelType.NGRAM.toString(), ModelType.DOBF.toString()));
    public final static DoubleEventField irenProbability = EventFields.Double("iren_probability");

    @Override
    public @NonNls String getGroupId() {
        return LookupUsageTracker.GROUP_ID;
    }

    @Override
    public String getEventId() {
        return LookupUsageTracker.FINISHED_EVENT_ID;
    }

    @Override
    public List<EventField> getExtensionFields() {
        return Arrays.asList(irenModelType, irenProbability);
    }
}
