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
public class KotlinCollisionTest extends CollisionTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + KotlinFileType.EXTENSION;
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new KotlinLanguageSupporter();
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
            "true, collision1",
            "true, collision2",
            "false, not_collision",
            "false, boi"
    })
    public void testField(boolean isColliding, String name) {
        testCollision(isColliding, name);
    }

    @Test
    @Parameters({
            "true, collision",
            "false, boi"
    })
    public void testParameter(boolean isColliding, String name) {
        testCollision(isColliding, name);
    }
}
