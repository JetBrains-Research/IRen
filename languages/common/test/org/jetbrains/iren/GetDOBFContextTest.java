package org.jetbrains.iren;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

public abstract class GetDOBFContextTest extends GetContextTest {
    @Override
    protected @NotNull String getTestFileNameResult() {
        return "/dobfContext/" + getTestName(true) + ".txt";
    }

    @Override
    public Object invokeSupporterFunction() {
        @NotNull LanguageSupporter supporter = getLanguageSupporter();
        final PsiElement variable = getTargetElementAtCaret();
        assertTrue(supporter.isVariableDeclarationOrReference(variable));
        return supporter.getDOBFContext((PsiNameIdentifierOwner) variable);
    }
}
