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
public class KotlinVariableTest extends VariableTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new KotlinLanguageSupporter();
    }

    @Test
    @Parameters({"0", "1"})
    public void testNotVariable(int index) {
        doTestNotVariable(index);
    }

    @Test
    @Parameters({"0", "1", "2", "3", "4", "5", "6"})
    public void testVariableDeclaration(int index) {
        doTestVariableDeclaration(index, true);
    }

    @Test
    @Parameters({"0", "1", "2", "3"})
    public void testNotVariableDeclaration(int index) {
        doTestVariableDeclaration(index,false);
    }
}
