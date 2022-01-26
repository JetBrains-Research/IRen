package org.jetbrains.iren;

import com.intellij.ide.highlighter.JavaFileType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class JavaInspectionHeuristicsTest extends InspectionHeuristicsTest {
    @Override
    public @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new JavaLanguageSupporter();
    }

    @Test
    @Parameters({"0", "1", "2", "3", "4", "5"})
    public void testExclude(int index) {
        doTestExclusionFromInspectionHeuristics(index, true);
    }

    @Test
    @Parameters({"0", "1"})
    public void testNotExclude(int index) {
        doTestExclusionFromInspectionHeuristics(index,false);
    }
}
