package org.jetbrains.iren;

import com.intellij.openapi.editor.Caret;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

public abstract class InspectionHeuristicsTest extends LanguageSupporterTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "excludeFromInspection/";
    }

    protected void doTestExclusionFromInspectionHeuristics(int index, boolean b) {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        Caret caret = getEditor().getCaretModel().getAllCarets().get(index);
        final PsiElement element = getTargetElementAtCaret(caret);
        assertTrue(element instanceof PsiNameIdentifierOwner);
        assertEquals(b, supporter.excludeFromInspection((PsiNameIdentifierOwner) element));
    }
}
