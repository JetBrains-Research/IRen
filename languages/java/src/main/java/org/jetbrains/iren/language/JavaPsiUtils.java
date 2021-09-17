package org.jetbrains.iren.language;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.utils.PsiUtilsBase;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.iren.utils.StringUtils.*;

public class JavaPsiUtils extends PsiUtilsBase {
    public static final List<String> NumberTypes = Arrays.asList("INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL");

    @Override
    public Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    protected String processLiteral(@NotNull PsiElement token) {
        String text = token.getText();
        if (text.contains("\n")) {
            return STRING_TOKEN;
        }
        if (token.getParent() instanceof PsiLiteral) {
            String literalType = ((PsiJavaToken) token).getTokenType().toString();
            if (literalType.equals("STRING_LITERAL")) {
                return text.length() > 10 ? STRING_TOKEN : text;
            }
            if (NumberTypes.contains(literalType)) {
                return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
            }
        }
        return null;
    }

    @Override
    public boolean isVariable(@Nullable PsiElement token) {
        return findDeclaration(token) instanceof PsiVariable;
    }

    @Override
    public boolean isIdentifier(PsiElement token) {
        return token instanceof PsiIdentifier;
    }
}
