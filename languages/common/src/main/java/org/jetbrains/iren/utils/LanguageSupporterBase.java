package org.jetbrains.iren.utils;

import com.google.common.collect.Streams;
import com.intellij.completion.ngram.slp.translating.Vocabulary;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.StandardProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.SmartList;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Contract;
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

    @Override
    public @NotNull Context<String> getContext(@NotNull PsiNameIdentifierOwner variable, boolean changeToUnknown) {
        PsiFile file = variable.getContainingFile();
        Collection<PsiElement> referenceElements = getReferenceElementsSet(variable, file);
        PsiElement root = findRoot(file, variable, referenceElements);
        List<Integer> varIdxs = new ArrayList<>(referenceElements.size() + 1);
        List<PsiElement> elements = getLeafElementsFromRoot(root);
        List<String> tokens = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < elements.size(); i++) {
            PsiElement element = elements.get(i);
            if (identifierIsVariableDeclaration(element)) {
                Pair<List<String>, Integer> listIdx = processVariableDeclaration(file, element);
                List<String> varWithType = listIdx.getFirst();
                int varIdx = listIdx.getSecond();
                tokens.addAll(varWithType);
                if (element.getParent().isEquivalentTo(variable)) {
                    varIdxs.add(i + offset + varIdx);
                    offset += varWithType.size() - 1;
                    continue;
                }
                offset += varWithType.size() - 1;
            } else {
                tokens.add(processToken(element));
            }
            if (referenceElements.contains(element)) {
                varIdxs.add(i + offset);
            }
        }
        Context<String> result = new Context<>(tokens, varIdxs);
        return changeToUnknown ? result.with(Vocabulary.unknownCharacter) : result;
    }

    private List<PsiElement> getLeafElementsFromRoot(PsiElement root) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(root)
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(this::shouldLex)
                .toList();
    }

    /**
     * @param variable declaration
     * @param file     where to find usages
     * @return usages without declaration
     */
    public Collection<PsiElement> getReferenceElementsSet(PsiNameIdentifierOwner variable, PsiFile file) {
        @Nullable Collection<PsiReference> references = findReferences(variable, file);
        return references != null ?
                references.stream()
                        .map(this::getIdentifier)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()) :
                Collections.emptyList();
    }

    public @Nullable Collection<PsiReference> findReferences(@NotNull PsiNameIdentifierOwner identifierOwner, @NotNull PsiFile file) {
//        Unknown problems when using GlobalSearchScope.projectScope. Most likely there are too many fields and searching breaks.
        return runForSomeTime(100, () -> RenamePsiElementProcessor.forElement(identifierOwner)
                .findReferences(identifierOwner, GlobalSearchScope.fileScope(file), false));
    }

    public static <T> @Nullable T runForSomeTime(long runningTimeMs, @NotNull Computable<T> process) {
        try {
            return ProgressManager.getInstance().runProcess(process, new LimitedRunningTimeIndicator(runningTimeMs));
        } catch (ProcessCanceledException e) {
//            System.out.println("Canceled");
            return null;
        }
    }

    private @Nullable PsiElement getIdentifier(Object element) {
        @Nullable PsiElement result = null;
        if (element instanceof PsiNameIdentifierOwner) {
            result = ((PsiNameIdentifierOwner) element).getNameIdentifier();
        } else if (element instanceof PsiReference) {
            result = getIdentifierFromReference((PsiReference) element);
        }
        return result;
    }

    private PsiElement getIdentifierFromReference(PsiReference reference) {
        @Nullable ASTNode element = reference.getElement().getNode().findChildByType(getIdentifierType());
        return element == null ? null : element.getPsi();
    }

    private @NotNull PsiElement findRoot(@NotNull PsiFile file, @NotNull PsiNameIdentifierOwner variable, @NotNull Collection<PsiElement> references) {
        if (references.size() == 0) return variable.getParent().getParent();
//        TODO: use PsiTreeUtil.findCommonParent
        List<Set<PsiElement>> parents = Streams.concat(Stream.of(variable), references.stream())
                .map(this::getParents)
                .collect(Collectors.toList());
        Set<PsiElement> common = parents.remove(0);
        parents.forEach(common::retainAll);
        Optional<PsiElement> res = common.stream().findFirst();
        return res.orElseGet(() -> variable.getParent().getParent());
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

    public boolean shouldLex(@NotNull PsiElement element) {
        return isLeaf(element) && !isBlank(element);
    }

    public boolean isLeaf(@NotNull PsiElement element) {
        return element.getFirstChild() == null;
    }

    public boolean isBlank(@NotNull PsiElement element) {
        return StringUtils.isBlank(element.getText());
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

    /**
     * Adds variable type if needed
     *
     * @param file       variable containing file
     * @param identifier identifier of the declaration
     * @return First: list of the variable type and name in a language specific order.
     * Second: index of the variable name in the list.
     */
    protected abstract @NotNull Pair<List<String>, Integer> processVariableDeclaration(@NotNull PsiFile file, @NotNull PsiElement identifier);

    protected abstract String processLiteral(@NotNull PsiElement token, @NotNull String text);

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
                .flatMap(element -> {
                    if (identifierIsVariableDeclaration(element)) {
                        return processVariableDeclaration(file, element).getFirst().stream();
                    } else {
                        return Stream.of(processToken(element));
                    }
                })
                .collect(Collectors.toList());
    }

    public boolean isVariable(@Nullable PsiElement token) {
        if (token == null) return false;
        if (token instanceof PsiNameIdentifierOwner) {
            return isVariableClass(token);
        }
        @Nullable PsiNameIdentifierOwner declaration = findDeclaration(token);
        return declaration != null && isVariableClass(declaration);
    }

    @Contract("null -> false")
    public boolean identifierIsVariableDeclaration(@Nullable PsiElement token) {
        return isIdentifier(token) && elementIsVariableDeclaration(token.getParent());
    }

    public boolean elementIsVariableDeclaration(@Nullable PsiElement element) {
        return element instanceof PsiNameIdentifierOwner && isVariableClass(element);
    }

    @Override
    public void printAvgTime() {
        System.out.printf("%s\t%s\ttotal: %d%n", this.getClass().getSimpleName(), total > 0 ? Duration.ofNanos(time / total).toString() : "0", total);
        time = 0;
        total = 0;
    }

    @Override
    public boolean isStopName(@NotNull String name) {
        return false;
    }

    @Override
    public boolean isVariableClass(@NotNull PsiElement token) {
        return getVariableClasses().stream().anyMatch(cls -> cls.isAssignableFrom(token.getClass()));
    }

    public boolean isVariableOrReference(@NotNull PsiNameIdentifierOwner variable, @Nullable PsiElement token) {
        if (token == null) return false;
        return PsiManager.getInstance(variable.getProject())
                .areElementsEquivalent(variable, findDeclaration(token));
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

    public @NotNull List<PsiElement> findVarIdentifiersUnderNode(@Nullable PsiElement node) {
        return SyntaxTraverser.psiTraverser()
                .withRoot(node)
                .forceIgnore(n -> n instanceof PsiComment)
                .filter(this::isVariable)
                .toList();
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
    public boolean isCanceled() {
        if (super.isCanceled()) {
            return true;
        }
        return (System.currentTimeMillis() - startTime) > runningTimeMs;
    }
}