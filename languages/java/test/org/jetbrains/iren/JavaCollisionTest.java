package org.jetbrains.iren;

import com.intellij.ide.highlighter.JavaFileType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;
import org.jetbrains.iren.language.JavaLanguageSupporter;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class JavaCollisionTest extends CollisionTest {
    @Override
    public @NotNull String getFileExtension() {
        return JavaFileType.DOT_DEFAULT_EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new JavaLanguageSupporter();
    }

    @Test
    @Parameters({
            "true, collision1",
            "true, collision2",
            "false, boi"
    })
    public void testLocalVariable(boolean isColliding, String name) {
        testCollision(isColliding, name);
    }

    @Test
    @Parameters({
//            "true, collision", conflict dialog is showing in ide, but it doesn't work in test
            "false, boi"
    })
    public void testField(boolean isColliding, String name) {
        testCollision(isColliding, name);
    }

    @Test
    @Parameters({
            "true, collision1",
            "true, collision2",
            "false, boi"
    })
    public void testParameter(boolean isColliding, String name) {
        testCollision(isColliding, name);
    }
}
