package tools.graphVarMiner;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.utils.PsiUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.jetbrains.iren.utils.PsiUtils.*;

public class JavaGraphExtractor {
    public final static String NEXT_TOKEN = "NextToken";
    public final static String LAST_LEXICAL_USE = "LastLexicalUse";

    public final Graph<PsiElement> codeGraph = new Graph<>();
    public final Map<String, PsiElement> lastLexicalUsages = new HashMap<>();
    public final PsiFile file;

    public JavaGraphExtractor(PsiFile file) {
        this.file = file;
//         Add token-level info
        PsiElement lastToken = null;
        for (PsiElement token : SyntaxTraverser.psiTraverser()
                .withRoot(file)
                .onRange(new TextRange(0, 64 * 1024)) // first 128 KB of chars
                .forceIgnore(node -> node instanceof PsiComment)
                .filter(PsiUtils::shouldLex)) {
            if (lastToken != null) {
                codeGraph.addEdge(lastToken, token, NEXT_TOKEN, true);
            }
            lastToken = token;

            if (isVariable(token)) {
                String text = token.getText();
                if (lastLexicalUsages.containsKey(text)) {
                    codeGraph.addEdge(token, lastLexicalUsages.get(text), LAST_LEXICAL_USE, true);
                }
                lastLexicalUsages.put(text, token);
            }
        }

//        Now add AST-level info
        file.accept(new AstGraphCreator(codeGraph));
//        Now add data-flow info
        file.accept(new DataflowGraphExtractor(codeGraph));
    }

    public @Nullable Graph<PsiElement> createGraph(PsiVariable variable) {
        PsiIdentifier identifier = variable.getNameIdentifier();
        if (identifier == null) return null;
        String name = identifier.getText();
        PsiElement currentNode = lastLexicalUsages.get(name);
        if (currentNode == null) return null;

        Graph<PsiElement> graph = codeGraph.shallowCopy(LAST_LEXICAL_USE);
        PsiElement lastRemoved = null;
        PsiElement lastVarRemoved = null;
        PsiElement previousNode = graph.getFirstChild(currentNode, LAST_LEXICAL_USE);
        while (previousNode != null) {
            boolean isPrevVar = isVariableOrReference(variable, previousNode);
            boolean isCurVar = isVariableOrReference(variable, currentNode);
            if (isPrevVar && !isCurVar) {
                graph.removeEdge(currentNode, previousNode, LAST_LEXICAL_USE, true);
                graph.addEdge(lastVarRemoved, previousNode, LAST_LEXICAL_USE, true);
                lastRemoved = currentNode;
            } else if (!isPrevVar && isCurVar) {
                graph.removeEdge(currentNode, previousNode, LAST_LEXICAL_USE, true);
                graph.addEdge(lastRemoved, previousNode, LAST_LEXICAL_USE, true);
                lastVarRemoved = currentNode;
            }
            currentNode = previousNode;
            previousNode = graph.getFirstChild(currentNode, LAST_LEXICAL_USE);
        }
        return createSubgraph(graph, variable);
    }

    private Graph<PsiElement> createSubgraph(Graph<PsiElement> graph, PsiVariable variable) {
        Graph<PsiElement> subgraph = new Graph<>();
        Stream.concat(Stream.of(variable), findReferences(variable, this.file))
                .map(PsiUtils::getIdentifier)
                .forEach(identifier -> subgraph.copyEdgesFromNode(identifier, graph, 8));
        return subgraph;
    }
}