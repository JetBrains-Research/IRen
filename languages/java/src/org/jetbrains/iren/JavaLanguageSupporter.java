package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandler;
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.jetbrains.iren.utils.StringUtils.*;

public class JavaLanguageSupporter extends LanguageSupporterBase {
    public static final TokenSet NumberTypes = TokenSet.create(JavaTokenType.INTEGER_LITERAL,
            JavaTokenType.LONG_LITERAL,
            JavaTokenType.FLOAT_LITERAL,
            JavaTokenType.DOUBLE_LITERAL);
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(PsiVariable.class);

    @Override
    public @NotNull Language getLanguage() {
        return JavaLanguage.INSTANCE;
    }

    @Override
    public @NotNull FileType getFileType() {
        return JavaFileType.INSTANCE;
    }

    @Override
    public @NotNull IElementType getIdentifierType() {
        return JavaTokenType.IDENTIFIER;
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
            @NotNull IElementType literalType = token.getNode().getElementType();
            if (literalType == JavaTokenType.STRING_LITERAL) {
                return STRING_TOKEN;
            }
            if (NumberTypes.contains(literalType)) {
                return INTEGERS_TO_LEAVE.contains(text) ? text : NUMBER_TOKEN;
            }
        }
        return null;
    }

    @Override
    protected Collection<Class<? extends PsiNameIdentifierOwner>> getFunctionAndClassPsi() {
        return Set.of(PsiMethod.class, PsiClass.class);
    }

    @Override
    protected PsiElement resolveReference(@NotNull PsiElement element) {
        return element instanceof PsiReference ? ((PsiReference) element).resolve() : null;
    }

    @Override
    public boolean isIdentifier(@Nullable PsiElement token) {
        return token instanceof PsiIdentifier;
    }

    @Override
    public @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder) {
        return new JavaVariableVisitor(holder);
    }

    @Override
    public boolean excludeFromInspection(@NotNull PsiNameIdentifierOwner variable) {
        return super.excludeFromInspection(variable) ||
                variable instanceof PsiParameter &&
                        variable.getParent() instanceof PsiCatchSection &&
                        PsiUtil.isIgnoredName(variable.getName());
    }

    @Override
    public boolean dobfReady() {
        return true;
    }

    @Override
    public @Nullable NameSuggestionProvider getNameSuggestionProvider() {
        return new MyJavaNameSuggestionProvider();
    }
}
