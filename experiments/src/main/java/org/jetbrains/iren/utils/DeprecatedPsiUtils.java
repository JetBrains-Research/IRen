package org.jetbrains.iren.utils;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.rename.JavaUnresolvableLocalCollisionDetector;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;
import org.jetbrains.iren.storages.Vocabulary;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.iren.utils.StringUtils.*;

public class DeprecatedPsiUtils {
    public static final List<String> NumberTypes = Arrays.asList("INTEGER_LITERAL", "LONG_LITERAL", "FLOAT_LITERAL", "DOUBLE_LITERAL");
    /**
     * Checks if there is variables with newName in the scope.
     *
     * @param element element for which we suggest newName.
     * @param newName new name of the {@param element}.
     * @return if there are collisions.
     */
    public static boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        List<UsageInfo> info = new SmartList<>();
        JavaUnresolvableLocalCollisionDetector.findCollisions(element, newName, info);
        return !info.isEmpty();
    }

    public static @NotNull Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner, @NotNull PsiFile file) {
//        Unknown problems when using GlobalSearchScope.projectScope. Most likely there are too many fields and searching breaks.
        return ReferencesSearch.search(identifierOwner, GlobalSearchScope.fileScope(file))
                .findAll()
                .stream();
    }

    public static @Nullable PsiIdentifier getIdentifier(Object element) {
        if (element instanceof PsiNameIdentifierOwner) {
            element = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReferenceExpression) {
            element = ((PsiReferenceExpression) element).getReferenceNameElement();
        }
        return ObjectUtils.tryCast(element, PsiIdentifier.class);
    }

    public static @NotNull String processToken(@NotNull PsiElement token) {
        return processToken(token, null);
    }

    public static @NotNull String processToken(@NotNull PsiElement token, @Nullable PsiVariable variable) {
        String text = token.getText();
        if (text.contains("\n")) {
            text = STRING_TOKEN;
        }
        if (token.getParent() instanceof PsiLiteral) {
            String literalType = ((PsiJavaToken) token).getTokenType().toString();
            if (literalType.equals("STRING_LITERAL")) {
                return text.length() > 10 ? STRING_TOKEN : text;
            }
            if (NumberTypes.contains(literalType)) {
                return INTEGERS_TO_LEAVE.contains(text) ? text : NUMBER_TOKEN;
            }
        } else if (variable != null && isVariableOrReference(variable, token)) {
            return VARIABLE_TOKEN;
        }
        return text;
    }

    public static boolean isVariableOrReference(@NotNull PsiVariable variable, @Nullable PsiElement token) {
        return PsiManager.getInstance(variable.getProject())
                .areElementsEquivalent(variable, findVariableDeclaration(token));
    }

    public static boolean isVariable(@Nullable PsiElement token) {
        return findVariableDeclaration(token) != null;
    }

    public static @Nullable PsiVariable findVariableDeclaration(@Nullable PsiElement token) {
        if (token instanceof PsiIdentifier) {
            PsiElement parent = token.getParent();
            PsiElement declaration = parent instanceof PsiReferenceExpression ?
                    resolveReference((PsiReferenceExpression) parent) : parent;
            if (declaration instanceof PsiVariable) {
                return (PsiVariable) declaration;
            }
        }
        return null;
    }

    public static @Nullable PsiElement resolveReference(@NotNull PsiReference reference) {
//        System.out.printf("Resolving reference: %s...\r", reference.toString().replace("\n", ""));
//        return runForSomeTime(reference::resolve, 1000);
        return reference.resolve();
    }

    public static <T> @Nullable T runForSomeTime(@NotNull Computable<T> process, long runningTime) {
        ProgressManager progressManager = ProgressManager.getInstance();
        try {
            return progressManager.runProcess(process,
                    new AbstractProgressIndicatorBase() {
                        final long startTime = System.currentTimeMillis();

                        @Override
                        public void checkCanceled() {
                            super.checkCanceled();
                        }

                        @Override
                        public boolean isCanceled() {
                            if (super.isCanceled()) {
                                return true;
                            }
                            if ((System.currentTimeMillis() - startTime) > runningTime) {
                                cancel();
                                return true;
                            }
                            return false;
                        }
                    });
        } catch (ProcessCanceledException e) {
//            System.out.println();
//            System.out.println("Canceled");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static @NotNull List<PsiIdentifier> findVarIdentifiersUnderNode(@Nullable PsiElement node) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(node)
                .forceIgnore(n -> n instanceof PsiComment)
                .filter(DeprecatedPsiUtils::isVariable)
                .toList()
                .stream()
                .map(i -> (PsiIdentifier) i)
                .collect(Collectors.toList());
    }


    public static boolean shouldLex(@NotNull PsiElement element) {
        return isLeaf(element) && !isBlank(element);
    }

    public static boolean isBlank(@NotNull PsiElement element) {
        return StringUtils.isBlank(element.getText());
    }

    public static boolean isLeaf(@NotNull PsiElement element) {
        return element.getFirstChild() == null;
    }

    public static @NotNull Set<PsiElement> getParents(@NotNull PsiElement element) {
        Set<PsiElement> parents = new LinkedHashSet<>();
        PsiElement parent = element.getParent();
        while (!(parent instanceof PsiFile)) {
            parents.add(parent);
            parent = parent.getParent();
        }
        return parents;
    }

    public static @NotNull Context<String> getContext(@NotNull PsiVariable variable, boolean changeToUnknown) {
        PsiElement root = findRoot(variable);
        List<Integer> varIdxs = new ArrayList<>();
        List<PsiElement> elements = SyntaxTraverser.psiTraverser()
                .withRoot(root)
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(DeprecatedPsiUtils::shouldLex)
                .toList();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            if (isVariableOrReference(variable, element)) {
                varIdxs.add(i);
                tokens.add(changeToUnknown ? Vocabulary.unknownCharacter : element.getText());
            } else {
                tokens.add(processToken(element));
            }
        }
        return new Context<>(tokens, varIdxs);
    }

    public static @NotNull PsiElement findRoot(@NotNull PsiVariable variable) {
        PsiFile file = variable.getContainingFile();
        Stream<PsiReference> elementUsages = findReferences(variable, file);
        List<Set<PsiElement>> parents = Stream.concat(Stream.of(variable), elementUsages)
                .map(DeprecatedPsiUtils::getIdentifier)
                .filter(Objects::nonNull)
                .map(DeprecatedPsiUtils::getParents)
                .collect(Collectors.toList());
        Set<PsiElement> common = parents.remove(0);
        parents.forEach(common::retainAll);
        Optional<PsiElement> res = common.stream().findFirst();
        return res.orElse(file);
    }

    public static @NotNull List<String> lexPsiFile(@NotNull PsiFile file) {
        return lexPsiFile(file, null);
    }

    public static @NotNull List<String> lexPsiFile(@NotNull PsiFile file, @Nullable Consumer<PsiElement> consumer) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(DeprecatedPsiUtils::shouldLex)
                .toList()
                .stream()
                .peek(x -> {
                    if (consumer != null) {
                        consumer.accept(x);
                    }
                })
                .map(DeprecatedPsiUtils::processToken)
                .collect(Collectors.toList());
    }
}
