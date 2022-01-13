package org.jetbrains.iren;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;

public abstract class CollisionTest extends LanguageSupporterTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "collision/";
    }

    protected void testCollision(boolean isColliding, String name) {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        final PsiElement variable = getTargetElementAtCaret();
        assertTrue(supporter.isVariable(variable));
        assertEquals(isColliding, supporter.isColliding(variable, name));
    }
}
