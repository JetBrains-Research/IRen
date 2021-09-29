package org.jetbrains.iren.language;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.utils.LanguageSupporterBase;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtParameter;
import org.jetbrains.kotlin.psi.KtProperty;

import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.iren.utils.StringUtils.*;

public class KotlinLanguageSupporter extends LanguageSupporterBase {
    public static final TokenSet NumberTypes = TokenSet.create(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL);
    private static final List<Class<? extends PsiNameIdentifierOwner>> variableClasses = new ArrayList<>();

    static {
        variableClasses.add(KtParameter.class);
        variableClasses.add(KtProperty.class);
        NGramVariableNamesContributor.SUPPORTED_TYPES.addAll(variableClasses);
    }

    @Override
    public @NotNull Language getLanguage() {
        return KotlinLanguage.INSTANCE;
    }

    @Override
    public @NotNull FileType getFileType() {
        return KotlinFileType.INSTANCE;
    }

    @Override
    public @NotNull List<Class<? extends PsiNameIdentifierOwner>> getVariableClasses() {
        return variableClasses;
    }

    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinRenameDispatcherHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinMemberInplaceRenameHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinVariableInplaceRenameHandler.class);
    }

    @Override
    protected String processLiteral(@NotNull PsiElement token, @NotNull String text) {
        @NotNull IElementType tokenType = token.getNode().getElementType();
        if (tokenType == KtTokens.REGULAR_STRING_PART) {
            return text.length() > 10 ? STRING_TOKEN : text;
        } else if (NumberTypes.contains(tokenType)) {
            return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
        }
        return null;
    }

    @Override
    protected PsiElement resolveReference(@NotNull PsiElement element) {
        KtNameReferenceExpression refExpr = element instanceof KtNameReferenceExpression ? (KtNameReferenceExpression) element : null;
        if (refExpr == null) return null;
//            final UReferenceExpression reference = UastContextKt.toUElement(element, UReferenceExpression.class);
        PsiReference[] refs = refExpr.getReferences();
        return refs[0].resolve();
    }

    @Override
    public boolean isIdentifier(PsiElement token) {
        return token.getNode().getElementType() == KtTokens.IDENTIFIER;
    }
}
