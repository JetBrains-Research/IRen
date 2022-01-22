package org.jetbrains.iren;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class CollisionTest extends LanguageSupporterTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "collision/";
    }

    protected void testCollision(boolean isColliding, String name) {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        final PsiElement variable = getTargetElementAtCaret();
        assertTrue(supporter.isVariableDeclarationOrReference(variable));
        assertEquals(isColliding, supporter.isColliding(variable, name));
    }
}
