package org.jetbrains.iren.statistics;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor;
import com.intellij.internal.statistic.eventLog.FeatureUsageData;
import jnr.ffi.types.sa_family_t;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.rename.MyLookup;

@SuppressWarnings("UnstableApiUsage")
public class IRenLookupUsageDescriptor implements LookupUsageDescriptor {
    @Override
    public @NotNull String getExtensionKey() {
        return "iren";
    }

    @Override
    public void fillUsageData(@NotNull Lookup lookup, @NotNull FeatureUsageData usageData) {
        if (lookup instanceof LookupImpl) {
            int idx = ((LookupImpl) lookup).getSelectedIndex();
            if (idx >= lookup.getItems().size()) return;
            LookupElement lookupElement = lookup.getItems().get(idx);
            @Nullable String model_type = lookupElement.getUserData(MyLookup.model_type);
            if (model_type == null) return;
            usageData.addData("iren_model_type", model_type);
            if (model_type.equals(MyLookup.NGram.MODEL_TYPE)) {
                @Nullable Double probability = lookupElement.getUserData(MyLookup.NGram.probability);
                if (probability == null) return;
                usageData.addData("iren_probability", probability);
            }
        }
    }
}
