package org.jetbrains.iren.utils;

import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
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

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jetbrains.iren.utils.StringUtils.STRING_TOKEN;

public abstract class LanguageSupporterBase implements LanguageSupporter {
    protected long time = 0;
    protected long total = 0;

    @Override
    public boolean isColliding(@NotNull PsiElement element, @NotNull String newName) {
        List<UsageInfo> info = new SmartList<>();
        RenamePsiElementProcessor.forElement(element).findCollisions(element, newName, new HashMap<>(), info);
        return !info.isEmpty();
    }

    public @Nullable Collection<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner, @NotNull PsiFile file) {
//        Unknown problems when using GlobalSearchScope.projectScope. Most likely there are too many fields and searching breaks.
        return runForSomeTime(() -> RenamePsiElementProcessor.forElement(identifierOwner)
                .findReferences(identifierOwner, GlobalSearchScope.fileScope(file), false), 100);
    }

    public @NotNull String processToken(@NotNull PsiElement token) {
        String text = token.getText();
        if (text.contains("\n")) {
            return STRING_TOKEN;
        }
        String literal = processLiteral(token, text);
        if (literal != null) return literal;
        return text;
    }

    protected abstract String processLiteral(@NotNull PsiElement token, @NotNull String text);

    public boolean isVariableOrReference(@NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement token) {
        if (token == null) return false;
        return PsiManager.getInstance(variable.getProject())
                .areElementsEquivalent(variable, findDeclaration(token));
    }

    public boolean isVariable(@Nullable PsiElement token) {
        if (token == null) return false;
        if (isVariableDeclaration(token)) return true;
        @Nullable PsiNameIdentifierOwner declaration = findDeclaration(token);
        return declaration != null && isVariableClass(declaration);
    }

    public boolean isVariableDeclaration(@Nullable PsiElement token) {
        if (token == null) return false;
        if (token instanceof PsiNameIdentifierOwner) {
            return isVariableClass(token);
        }
        if (isIdentifier(token)) {
            PsiElement parent = token.getParent();
            return parent instanceof PsiNameIdentifierOwner && isVariableClass(parent);
        }
        return false;
    }

    protected boolean isVariableClass(@NotNull PsiElement token) {
        return getVariableClasses().stream().anyMatch(cls -> cls.isAssignableFrom(token.getClass()));
    }

    public @Nullable PsiNameIdentifierOwner findDeclaration(@NotNull PsiElement token) {
        if (isIdentifier(token)) {
            PsiElement parent = token.getParent();
            if (parent == null) return null;
            PsiElement declaration = parent instanceof PsiNameIdentifierOwner ? parent : resolveReference(parent);
            if (declaration instanceof PsiNameIdentifierOwner) {
                return (PsiNameIdentifierOwner) declaration;
            }
        }
        return null;
    }

    protected abstract @Nullable PsiElement resolveReference(@NotNull PsiElement reference);

    public <T> @Nullable T runForSomeTime(@NotNull Computable<T> process, long runningTimeMs) {
        ProgressManager progressManager = ProgressManager.getInstance();
        try {
            return progressManager.runProcess(process, new LimitedRunningTimeIndicator(runningTimeMs));
        } catch (ProcessCanceledException e) {
//            System.out.println("Canceled");
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

    @Override
    public @NotNull Context<String> getContext(@NotNull PsiNameIdentifierOwner variable, boolean changeToUnknown) {
        PsiFile file = variable.getContainingFile();
        Collection<PsiElement> usages = findUsages(variable, file);
        PsiElement root = findRoot(file, usages);
        List<Integer> varIdxs = new ArrayList<>();
        List<PsiElement> elements = SyntaxTraverser.psiTraverser()
                .withRoot(root)
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(this::shouldLex)
                .toList();
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            if (usages.contains(element)) {
                varIdxs.add(i);
                tokens.add(changeToUnknown ? Vocabulary.unknownCharacter : element.getText());
            } else {
                tokens.add(processToken(element));
            }
        }
        return new Context<>(tokens, varIdxs);
    }

    public Collection<PsiElement> findUsages(PsiNameIdentifierOwner variable, PsiFile file) {
        @Nullable Collection<PsiReference> references = findReferences(variable, file);
        return Stream.concat(Stream.of(variable), references != null ? references.stream() : Stream.empty())
                .map(this::getIdentifier)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private @NotNull PsiElement findRoot(@NotNull PsiFile file, @NotNull Collection<PsiElement> usages) {
        if (usages.size() < 2) return file;
        List<Set<PsiElement>> parents = usages.stream()
                .map(this::getParents)
                .collect(Collectors.toList());
        Set<PsiElement> common = parents.remove(0);
        parents.forEach(common::retainAll);
        Optional<PsiElement> res = common.stream().findFirst();
        return res.orElse(file);
    }

    private @Nullable PsiElement getIdentifier(Object element) {
        @Nullable PsiElement result = null;
        if (element instanceof PsiNameIdentifierOwner) {
            result = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReference) {
            result = ((PsiReference) element).getElement();
        }
        return result;
    }

    private @NotNull Set<PsiElement> getParents(@NotNull PsiElement element) {
        Set<PsiElement> parents = new LinkedHashSet<>();
        PsiElement parent = element.getParent();
        while (!(parent instanceof PsiFile)) {
            parents.add(parent);
            parent = parent.getParent();
        }
        return parents;
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
                .peek(element -> {
                    if (consumer != null) {
                        consumer.accept(element);
                    }
                })
                .map(this::processToken)
                .collect(Collectors.toList());
    }

    @Override
    public void printAvgTime() {
        System.out.printf("%s\t%s\ttotal: %d%n", this.getClass().getSimpleName(), total > 0 ? Duration.ofNanos(time / total).toString() : "0", total);
        time = 0;
        total = 0;
    }

    /**
     * Wrapper for a function execution time of which you want to measure.
     *
     * @param supplier function
     * @param <T>      return type
     * @return the result of the function call
     */
    protected <T> T measureTime(Supplier<T> supplier) {
        long start = System.nanoTime();
        try {
            return supplier.get();
        } finally {
            long t = System.nanoTime() - start;
            synchronized (this) {
                time += t;
                total += 1;
            }
        }
    }
}

class LimitedRunningTimeIndicator extends AbstractProgressIndicatorBase implements StandardProgressIndicator {
    final long startTime = System.currentTimeMillis();
    private final long runningTimeMs;

    public LimitedRunningTimeIndicator(long runningTimeMs) {
        this.runningTimeMs = runningTimeMs;
    }

    @Override
    public void checkCanceled() {
        super.checkCanceled();
    }

    @Override
    public boolean isCanceled() {
        if (super.isCanceled()) {
            return true;
        }
        if ((System.currentTimeMillis() - startTime) > runningTimeMs) {
            cancel();
            return true;
        }
        return false;
    }
}