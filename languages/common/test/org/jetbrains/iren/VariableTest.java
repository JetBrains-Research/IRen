package org.jetbrains.iren;

import com.intellij.openapi.editor.Caret;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.api.LanguageSupporter;

public abstract class VariableTest extends LanguageSupporterTest {
    @Override
    protected @NotNull String getTestDataBasePath() {
        return "variable/";
    }

    protected void doTestNotVariable(int index) {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        Caret caret = getEditor().getCaretModel().getAllCarets().get(index);
        final PsiElement element = getTargetElementAtCaret(caret);
        assertFalse(supporter.isVariableDeclarationOrReference(element));
    }

    protected void doTestVariableDeclaration(int index, boolean b) {
        configureByFile(getTestFileName());
        final LanguageSupporter supporter = getLanguageSupporter();
        Caret caret = getEditor().getCaretModel().getAllCarets().get(index);
        final PsiElement element = getFile().findElementAt(caret.getOffset());
        assertEquals(b, supporter.identifierIsVariableDeclaration(element));
    }
}
