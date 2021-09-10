package org.jetbrains.iren.statistics;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.rename.MyMemberInplaceRenamer;

@SuppressWarnings("UnstableApiUsage")
public class IRenLookupUsageDescriptor implements LookupUsageDescriptor {
    @Override
    public @NotNull String getExtensionKey() {
        return "iren";
    }

    @Override
    public void fillUsageData(@NotNull Lookup lookup, @NotNull FeatureUsageData usageData) {
        if (lookup instanceof MyMemberInplaceRenamer.NGramLookupElement) {
            usageData.addData("iren_model", "ngram");
            usageData.addData("iren_rank", ((MyMemberInplaceRenamer.NGramLookupElement) lookup).rank);
            usageData.addData("iren_probability", ((MyMemberInplaceRenamer.NGramLookupElement) lookup).probability);
        } else if (lookup instanceof MyMemberInplaceRenamer.DefaultLookupElement) {
            usageData.addData("iren_model", "default");
        }
    }
}
