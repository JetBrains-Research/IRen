package tools.graphVarMiner;

import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static org.jetbrains.id.names.suggesting.utils.PsiUtils.isBlank;
import static org.jetbrains.id.names.suggesting.utils.PsiUtils.isLeaf;

public class AstGraphCreator extends JavaRecursiveElementVisitor {

    public static final String CHILD_EDGE = "Child";

    private final Graph<PsiElement> graph;

    public AstGraphCreator(Graph<PsiElement> graph) {
        this.graph = graph;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (!isLeaf(element) || !isBlank(element)) {
            graph.addEdge(element.getParent(), element, CHILD_EDGE, true);
        }
        super.visitElement(element);
    }

    @Override
    public void visitComment(@NotNull PsiComment comment) {
        // ignore comments
    }
}