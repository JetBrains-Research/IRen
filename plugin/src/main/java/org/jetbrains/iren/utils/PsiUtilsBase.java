package org.jetbrains.iren.utils;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.storages.Context;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.iren.utils.StringUtils.VARIABLE_TOKEN;

public abstract class PsiUtilsBase implements PsiUtils {
    @Override
    public boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        List<UsageInfo> info = new SmartList<>();
        RenamePsiElementProcessor.forElement(element).findCollisions(element, newName, new HashMap<>(), info);
        return !info.isEmpty();
    }

    public @NotNull Stream<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner, @NotNull PsiFile file) {
//        Unknown problems when using GlobalSearchScope.projectScope. Most likely there are too many fields and searching breaks.
        return RenamePsiElementProcessor.forElement(identifierOwner)
                .findReferences(identifierOwner, GlobalSearchScope.fileScope(file), false)
                .stream();
    }

    public @Nullable PsiElement getIdentifier(Object element) {
        @Nullable PsiElement result = null;
        if (element instanceof PsiNameIdentifierOwner) {
            result = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReference) {
            result = ((PsiReference) element).getElement();
        }
        return result;
    }

    public @NotNull String processToken(@NotNull PsiElement token) {
        return processToken(token, null);
    }

    public @NotNull String processToken(@NotNull PsiElement token, @Nullable PsiNameIdentifierOwner variable) {
        String literal = processLiteral(token);
        if (literal != null) return literal;
        if (variable != null && isVariableOrReference(variable, token)) {
            return VARIABLE_TOKEN;
        }
        return token.getText();
    }

    protected abstract String processLiteral(@NotNull PsiElement token);

    public boolean isVariableOrReference(@NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement token) {
        return PsiManager.getInstance(variable.getProject())
                .areElementsEquivalent(variable, findDeclaration(token));
    }

    public abstract boolean isVariable(@Nullable PsiElement token);

    public @Nullable PsiNameIdentifierOwner findDeclaration(@Nullable PsiElement token) {
        if (token == null) return null;
        if (isIdentifier(token)) {
            PsiElement parent = token.getParent();
            PsiElement declaration = parent instanceof PsiReference ?
                    resolveReference((PsiReference) parent) : parent;
            if (declaration instanceof PsiNameIdentifierOwner) {
                return (PsiNameIdentifierOwner) declaration;
            }
        }
        return null;
    }

    public abstract boolean isIdentifier(PsiElement token);

    public @Nullable PsiElement resolveReference(@NotNull PsiReference reference) {
//        System.out.printf("Resolving reference: %s...\r", reference.toString().replace("\n", ""));
//        return runForSomeTime(reference::resolve, 1000);
        return reference.resolve();
    }

    public <T> @Nullable T runForSomeTime(@NotNull Computable<T> process, long runningTime) {
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
            System.out.println("Canceled");
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public @NotNull List<PsiElement> findVarIdentifiersUnderNode(@Nullable PsiElement node) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(node)
                .forceIgnore(n -> n instanceof PsiComment)
                .filter(this::isVariable)
                .toList();
    }


    public boolean shouldLex(@NotNull PsiElement element) {
        return isLeaf(element) && !isBlank(element);
    }

    public boolean isBlank(@NotNull PsiElement element) {
        return StringUtils.isBlank(element.getText());
    }

    public boolean isLeaf(@NotNull PsiElement element) {
        return element.getFirstChild() == null;
    }

    public @NotNull Set<PsiElement> getParents(@NotNull PsiElement element) {
        Set<PsiElement> parents = new LinkedHashSet<>();
        PsiElement parent = element.getParent();
        while (!(parent instanceof PsiFile)) {
            parents.add(parent);
            parent = parent.getParent();
        }
        return parents;
    }

    @Override
    public @NotNull Context<String> getContext(@NotNull PsiNameIdentifierOwner variable, boolean changeToUnknown) {
        PsiElement root = findRoot(variable);
        List<Integer> varIdxs = new ArrayList<>();
        List<PsiElement> elements = SyntaxTraverser.psiTraverser()
                .withRoot(root)
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(this::shouldLex)
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

    public @NotNull PsiElement findRoot(@NotNull PsiNameIdentifierOwner variable) {
        PsiFile file = variable.getContainingFile();
        Stream<PsiReference> elementUsages = findReferences(variable, file);
        List<Set<PsiElement>> parents = Stream.concat(Stream.of(variable), elementUsages)
                .map(this::getIdentifier)
                .filter(Objects::nonNull)
                .map(this::getParents)
                .collect(Collectors.toList());
        Set<PsiElement> common = parents.remove(0);
        parents.forEach(common::retainAll);
        Optional<PsiElement> res = common.stream().findFirst();
        return res.orElse(file);
    }

    public @NotNull List<String> lexPsiFile(@NotNull PsiFile file) {
        return lexPsiFile(file, null);
    }

    public @NotNull List<String> lexPsiFile(@NotNull PsiFile file, @Nullable Consumer<PsiElement> consumer) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(this::shouldLex)
                .toList()
                .stream()
                .peek(x -> {
                    if (consumer != null) {
                        consumer.accept(x);
                    }
                })
                .map(this::processToken)
                .collect(Collectors.toList());
    }
}