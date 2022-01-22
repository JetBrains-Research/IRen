package org.jetbrains.iren;

import com.jetbrains.python.PythonFileType;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class PyCollisionTest extends CollisionTest {
    @Override
    public @NotNull String getFileExtension() {
        return "." + PythonFileType.INSTANCE.getDefaultExtension();
    }

    @Override
    public @NotNull LanguageSupporter getLanguageSupporter() {
        return new PyLanguageSupporter();
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
