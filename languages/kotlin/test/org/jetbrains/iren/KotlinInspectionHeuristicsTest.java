package org.jetbrains.iren;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.language.KotlinLanguageSupporter;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class KotlinInspectionHeuristicsTest extends InspectionHeuristicsTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new KotlinLanguageSupporter();
    }

    @Test
    @Parameters({"0", "1", "2", "3"})
    public void testExclude(int index) {
        doTestExclusionFromInspectionHeuristics(index, true);
    }

    @Test
    @Parameters({"0", "1"})
    public void testNotExclude(int index) {
        doTestExclusionFromInspectionHeuristics(index, false);
    }
}
