package org.jetbrains.iren.language;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.TypeUtilsKt;
import org.jetbrains.iren.contributors.NGramVariableNamesContributor;
import org.jetbrains.iren.inspections.variable.KotlinVariableVisitor;
import org.jetbrains.iren.utils.LanguageSupporterBase;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler;
import org.jetbrains.kotlin.idea.references.ReferenceUtilsKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.UnwrappedType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.iren.TypeUtilsKt.renderType;
import static org.jetbrains.iren.utils.StringUtils.*;

public class KotlinLanguageSupporter extends LanguageSupporterBase {
    public static final TokenSet NumberTypes = TokenSet.create(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL);
    private static final Collection<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(KtProperty.class, KtParameter.class);
    private static final Collection<String> stopNames = List.of("it");

    static {
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
    public @NotNull IElementType getIdentifierType() {
        return KtTokens.IDENTIFIER;
    }

    @Override
    public @NotNull Collection<Class<? extends PsiNameIdentifierOwner>> getVariableClasses() {
        return variableClasses;
    }

    @Override
    public void removeHandlers() {
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinRenameDispatcherHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinMemberInplaceRenameHandler.class);
        RenameHandler.EP_NAME.getPoint().unregisterExtension(KotlinVariableInplaceRenameHandler.class);
    }

    @Override
    protected @NotNull Pair<List<String>, Integer> processVariableDeclaration(@NotNull PsiFile file, @NotNull PsiElement identifier) {
        List<String> varWithType = new ArrayList<>(List.of(identifier.getText()));
        final @NotNull PsiElement parent = identifier.getParent();
        if (parent instanceof KtCallableDeclaration && ((KtCallableDeclaration) parent).getTypeReference() == null) {
            KotlinType type = runForSomeTime(5, () -> TypeUtilsKt.getType((KtDeclaration) parent));
            if (type != null) {
                UnwrappedType unwraped = type.unwrap();
                varWithType.add(":");
                varWithType.addAll(splitVariableType(renderType(unwraped).replaceAll("/\\*.*\\*/", "")));
            }
        }
        return new Pair<>(varWithType, 0);
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
        return ReferenceUtilsKt.getMainReference(refExpr).resolve();
    }

    @Override
    public boolean isIdentifier(@Nullable PsiElement token) {
        return token != null && token.getNode().getElementType() == KtTokens.IDENTIFIER;
    }

    @Override
    public boolean isStopName(@NotNull String name) {
        return stopNames.contains(name);
    }

    @Override
    public @NotNull PsiElementVisitor createVariableVisitor(@NotNull ProblemsHolder holder) {
        return new KotlinVariableVisitor(holder);
    }
}
