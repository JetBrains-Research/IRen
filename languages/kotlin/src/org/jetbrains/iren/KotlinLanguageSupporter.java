package org.jetbrains.iren;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.NameSuggestionProvider;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.builtins.KotlinBuiltIns;
import org.jetbrains.kotlin.descriptors.CallableDescriptor;
import org.jetbrains.kotlin.idea.KotlinFileType;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinMemberInplaceRenameHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinRenameDispatcherHandler;
import org.jetbrains.kotlin.idea.refactoring.rename.KotlinVariableInplaceRenameHandler;
import org.jetbrains.kotlin.idea.references.ReferenceUtilsKt;
import org.jetbrains.kotlin.lexer.KtTokens;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;
import org.jetbrains.kotlin.types.KotlinType;
import org.jetbrains.kotlin.types.typeUtil.TypeUtilsKt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jetbrains.iren.utils.StringUtils.*;

public class KotlinLanguageSupporter extends LanguageSupporterBase {
    public static final TokenSet NumberTypes = TokenSet.create(KtTokens.FLOAT_LITERAL, KtTokens.INTEGER_LITERAL);
    private static final Collection<Class<? extends PsiNameIdentifierOwner>> variableClasses = List.of(KtProperty.class, KtParameter.class);
    private static final Collection<String> stopNames = List.of("it");

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
    protected String processLiteral(@NotNull PsiElement token, @NotNull String text) {
        @NotNull IElementType tokenType = token.getNode().getElementType();
        if (tokenType == KtTokens.REGULAR_STRING_PART) {
            return STRING_TOKEN;
        } else if (NumberTypes.contains(tokenType)) {
            return IntegersToLeave.contains(text) ? text : NUMBER_TOKEN;
        }
        return null;
    }

    @Override
    protected Collection<Class<? extends PsiNameIdentifierOwner>> getFunctionAndClassPsi() {
        return List.of(KtFunction.class, KtClassOrObject.class);
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

    @Override
    public boolean excludeFromInspection(@NotNull PsiNameIdentifierOwner variable) {
        return super.excludeFromInspection(variable) || isOverridden(variable) || parseParameter(variable) || parseProperty(variable) || isSuffixOfType(variable);
    }

    private boolean parseParameter(PsiNameIdentifierOwner variable) {
        if (variable instanceof KtParameter) {
//            "Ignored" parameter in catch clause.
            final PsiElement parent = variable.getParent();
            if (parent != null && parent.getParent() instanceof KtCatchClause) {
                final String name = variable.getName();
                return PsiUtil.isIgnoredName(name) || Objects.equals(name, "_");
            }
//            Parameter of the overridden method.
            final KtParameter parameter = (KtParameter) variable;
            final PsiElement declaration = parameter.getOwnerFunction();
            return isOverridden(declaration);
        }
        return false;
    }

    private boolean parseProperty(PsiNameIdentifierOwner variable) {
        if (variable instanceof KtProperty) {
//            Variable name is a suffix of the reference name.
            KtExpression initializer = ((KtProperty) variable).getInitializer();
            if (initializer instanceof KtQualifiedExpression) {
                initializer = ((KtQualifiedExpression) initializer).getSelectorExpression();
                if (initializer == null) return false;
            }
            if (initializer instanceof KtCallExpression) {
                initializer = ((KtCallExpression) initializer).getCalleeExpression();
                if (initializer == null) return false;
            }
            if (initializer instanceof KtNameReferenceExpression) {
                return firstIsSuffixOfSecond(variable.getName(), ((KtNameReferenceExpression) initializer).getReferencedName());
            }
        }
        return false;
    }

    private boolean isSuffixOfType(@NotNull PsiNameIdentifierOwner variable) {
        CallableDescriptor callableDescriptor = (CallableDescriptor) ResolutionUtils.unsafeResolveToDescriptor((KtDeclaration) variable, BodyResolveMode.PARTIAL);
        KotlinType type = callableDescriptor.getReturnType();
        return type != null && !TypeUtilsKt.isUnit(type) &&
                !KotlinBuiltIns.isPrimitiveType(type) &&
                firstIsSuffixOfSecond(variable.getName(), type.toString());
    }

    private boolean isOverridden(PsiElement element) {
        if (element instanceof KtModifierListOwner) {
            return ((KtModifierListOwner) element).hasModifier(KtTokens.OVERRIDE_KEYWORD);
        }
        return false;
    }

    @Override
    public @Nullable NameSuggestionProvider getNameSuggestionProvider() {
        return new MyKotlinNameSuggestionProvider();
    }

    @Override
    public boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        return super.isColliding(element, newName) || isCollidingWithParameter(element, newName);
    }

    /**
     * Checks if enclosing {@link KtCallableDeclaration} has a parameter with newName.
     * Fix <a href="https://jetbrains.team/p/suggesting-identifier-names/issues/30">issue #30</a>.
     */
    private boolean isCollidingWithParameter(@NotNull PsiElement element, @NotNull String newName) {
        final KtCallableDeclaration parent = PsiTreeUtil.getParentOfType(element, KtCallableDeclaration.class);
        return parent != null && parent.getValueParameters().stream().anyMatch(parameter -> {
            final PsiElement nameIdentifier = parameter.getNameIdentifier();
            return nameIdentifier != null && newName.equals(nameIdentifier.getText());
        });
    }
}
