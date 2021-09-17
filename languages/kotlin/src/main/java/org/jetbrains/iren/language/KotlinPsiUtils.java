package org.jetbrains.iren.language;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.utils.PsiUtilsBase;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtParameter;

import static org.jetbrains.iren.utils.StringUtils.*;

public class KotlinPsiUtils extends PsiUtilsBase {
    public static final TokenSet NumberTypes = TokenSet.create(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL);

    @Override
    public Language getLanguage() {
        return KotlinLanguage.INSTANCE;
    }

    @Override
    protected String processLiteral(@NotNull PsiElement token) {
        String text = token.getText();
        @NotNull IElementType tokenType = token.getNode().getElementType();
        if (text.contains("\n")) {
            return STRING_TOKEN;
        } else if (tokenType == KtTokens.REGULAR_STRING_PART) {
            return text.length() > 10 ? STRING_TOKEN : text;
        } else if (NumberTypes.contains(tokenType)) {
            return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
        }

        return null;
    }

    @Override
    public boolean isVariable(@Nullable PsiElement token) {
        return findDeclaration(token) instanceof KtParameter;
    }

    @Override
    public boolean isIdentifier(PsiElement token) {
        return token.getNode().getElementType() == KtTokens.IDENTIFIER;
    }
}
