package org.jetbrains.iren.language;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.utils.LanguageSupporterBase;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.iren.utils.StringUtils.*;

public class JavaLanguageSupporter extends LanguageSupporterBase {
    public static final Collection<String> NumberTypes = Set.of("INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL");
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(PsiVariable.class);

    static {
        NGramVariableNamesContributor.SUPPORTED_TYPES.addAll(variableClasses);
    }

    @Override
    public @NotNull Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    public @NotNull FileType getFileType() {
        return JavaFileType.INSTANCE;
    }

    @Override
    public @NotNull Collection<Class<? extends PsiNameIdentifierOwner>> getVariableClasses() {
        return variableClasses;
    }

    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(VariableInplaceRenameHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(MemberInplaceRenameHandler.class);
    }

    @Override
    protected String processLiteral(@NotNull PsiElement token, @NotNull String text) {
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
    protected PsiElement resolveReference(@NotNull PsiElement element) {
        return element instanceof PsiReference ? ((PsiReference) element).resolve() : null;
    }

    @Override
    public boolean isIdentifier(PsiElement token) {
        return token instanceof PsiIdentifier;
    }
}
