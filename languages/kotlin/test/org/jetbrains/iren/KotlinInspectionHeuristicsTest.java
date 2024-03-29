package org.jetbrains.iren;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.KotlinFileType;

public class KotlinInspectionHeuristicsTest extends IRenInspectionTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new KotlinLanguageSupporter();
    }

    public void testInspection() {
        doTestInspection();
    }
}
