package tools.graphVarMiner;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.iren.utils.DeprecatedPsiUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static org.jetbrains.iren.utils.DeprecatedPsiUtils.*;

public class DataflowGraphExtractor extends JavaElementVisitor implements PsiRecursiveVisitor {
    public static final String LAST_USE = "LastUse";
    public static final String LAST_WRITE = "LastWrite";
    public static final String COMPUTED_FROM = "ComputedFrom";
    private static final String RETURNS_TO = "ReturnsTo";
    private static final String FORMAL_ARG_NAME = "FormalArgName";
    private static final String GUARDED_BY = "GuardedBy";
    private static final String GUARDED_BY_NEGATION = "GuardedByNegation";

    private final Graph<PsiElement> graph;
    private VarState varState = new VarState();

    public DataflowGraphExtractor(Graph<PsiElement> graph) {
        this.graph = graph;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        element.acceptChildren(this);
    }

    @Override
    public void visitVariable(PsiVariable variable) {
//        System.out.print("visitVariable\r");
        if (!(variable instanceof PsiLocalVariable) && varState.contains(variable)) return;
        varState.init(variable);
        acceptIfNotNull(variable.getInitializer());
        acceptIfNotNull(variable.getNameIdentifier());
        if (variable instanceof PsiParameter || variable.hasInitializer()) {
            PsiIdentifier identifier = variable.getNameIdentifier();
            if (identifier == null) return;
            varState.getLastWrite(variable).clear();
            varState.getLastWrite(variable).add(identifier);

            addEdgesFromIdToNodeIds(identifier, variable.getInitializer(), COMPUTED_FROM, null);
        }
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
    }

    @Override
    public void visitIdentifier(PsiIdentifier identifier) {
//        System.out.print("visitIdentifier\r");
        PsiVariable declaration = findVariableDeclaration(identifier);
        if (declaration == null) {
            return;
        }
        if (!varState.contains(declaration)) {
            declaration.accept(this);
        }

//        Add LastUse edges
        Collection<PsiIdentifier> lastUses = varState.getLastUse(declaration);
        for (PsiIdentifier lastUse : lastUses) {
            graph.addEdge(identifier, lastUse, LAST_USE, true);
        }
        lastUses.clear();
        lastUses.add(identifier);

//        Add LastWrite edges
        for (PsiIdentifier lastWrite : varState.getLastWrite(declaration)) {
            graph.addEdge(identifier, lastWrite, LAST_WRITE, true);
        }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
//        System.out.print("visitAssignmentExpression\r");
        PsiExpression rExpr = expression.getRExpression();
        acceptIfNotNull(rExpr);
        PsiExpression lExpr = expression.getLExpression();
        acceptIfNotNull(lExpr);
        if (expression.getOperationSign().getTokenType() != JavaTokenType.EQ) {
            acceptIfNotNull(lExpr);
        }

        PsiIdentifier identifier = getIdentifier(lExpr);
        if (identifier == null) return;
        PsiVariable variable = findVariableDeclaration(identifier);
        if (variable == null) return;
        if (!varState.contains(variable)) {
            variable.accept(this);
        }
        varState.getLastWrite(variable).clear();
        varState.getLastWrite(variable).add(identifier);

        addEdgesFromIdToNodeIds(identifier, rExpr, COMPUTED_FROM, null);

    }

    @Override
    public void visitUnaryExpression(PsiUnaryExpression expression) {
//        System.out.print("visitUnaryExpression\r");
        super.visitUnaryExpression(expression);
        super.visitUnaryExpression(expression);
        PsiIdentifier identifier = getIdentifier(expression.getOperand());
        if (identifier == null) return;
        PsiVariable variable = findVariableDeclaration(identifier);
        if (variable == null) return;
        if (!varState.contains(variable)) {
            variable.accept(this);
        }
        varState.getLastWrite(variable).clear();
        varState.getLastWrite(variable).add(identifier);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
//        System.out.print("visitReturnStatement\r");
        super.visitReturnStatement(statement);
//        Add ReturnsTo edges
        PsiElement element = statement;
        while (!(element instanceof PsiMethod)) {
            element = element.getParent();
            if (element instanceof PsiLambdaExpression || element instanceof PsiClass) {
                return;
            }
        }
        graph.addEdge(statement, element, RETURNS_TO, true);
    }

    @Override
    public void visitCallExpression(PsiCallExpression callExpression) {
//        System.out.print("visitCallExpression\r");
        super.visitCallExpression(callExpression);
//        Add FormalArgName edges
        PsiExpressionList exprList = callExpression.getArgumentList();
        if (exprList == null) return;
        PsiExpression[] expressions = exprList.getExpressions();
        if (expressions.length == 0) return;
        PsiMethod method = callExpression.resolveMethod();
        if (method == null) return;
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == expressions.length) {
            for (int i = 0; i < parameters.length; i++) {
                graph.addEdge(getIdentifier(expressions[i]), getIdentifier(parameters[i]), FORMAL_ARG_NAME, true);
            }
        }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
//        System.out.print("visitIfStatement\r");
        acceptIfNotNull(statement.getCondition());
        VarState conditionState = varState.copy();

        acceptIfNotNull(statement.getThenBranch());
        VarState postThenState = varState.copy();
        varState = conditionState;

        acceptIfNotNull(statement.getElseBranch());
        varState.mergeWith(postThenState);

//        Add GuardedBy and GuardedByNegation edges
        List<PsiVariable> includeOnly = findVarIdentifiersUnderNode(statement.getCondition())
                .stream()
                .map(DeprecatedPsiUtils::findVariableDeclaration)
                .collect(Collectors.toList());
        addEdgesFromNodeIdsToId(statement.getThenBranch(), statement.getCondition(), GUARDED_BY, includeOnly);
        addEdgesFromNodeIdsToId(statement.getElseBranch(), statement.getCondition(), GUARDED_BY_NEGATION, includeOnly);
    }

    private void addEdgesFromNodeIdsToId(PsiElement underNode, PsiElement toElement, @NotNull String edgeName, @Nullable List<PsiVariable> includeOnly) {
        for (PsiIdentifier identifier : findVarIdentifiersUnderNode(underNode)) {
            if (includeOnly == null || includeOnly.contains(findVariableDeclaration(identifier))) {
                graph.addEdge(identifier, toElement, edgeName, true);
            }
        }
    }

    private void addEdgesFromIdToNodeIds(PsiElement fromElement, PsiElement node, @NotNull String edgeName, @Nullable List<PsiVariable> includeOnly) {
        for (PsiIdentifier identifier : findVarIdentifiersUnderNode(node)) {
            if (includeOnly == null || includeOnly.contains(findVariableDeclaration(identifier))) {
                graph.addEdge(fromElement, identifier, edgeName, true);
            }
        }
    }

    public final IdentityHashMap<PsiStatement, List<VarState>> breakStatesMap = new IdentityHashMap<>();
    public final IdentityHashMap<PsiStatement, List<VarState>> continueStatesMap = new IdentityHashMap<>();

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
//        System.out.print("visitBreakStatement\r");
        addState(statement.findExitedStatement(), varState.copy(), breakStatesMap);
        super.visitBreakStatement(statement);
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
//        System.out.print("visitContinueStatement\r");
        addState(statement.findContinuedStatement(), varState.copy(), continueStatesMap);
        super.visitContinueStatement(statement);
    }

    @Override
    public void visitSwitchStatement(PsiSwitchStatement statement) {
//        System.out.print("visitSwitchStatement\r");
        acceptIfNotNull(statement.getExpression());

        PsiCodeBlock body = statement.getBody();
        if (body != null) {
            VarState defaultState = varState.copy();
            List<VarState> states = new ArrayList<>();
            int i = 0;
            for (PsiStatement child : body.getStatements()) {
                if (child instanceof PsiSwitchLabelStatement) {
                    if (i++ != 0) {
                        states.add(varState.copy());
                    }
                    varState = defaultState.copy();
                    if (!((PsiSwitchLabelStatement) child).isDefaultCase()) {
                        varState.mergeWith(states, false);
                        varState.subtract(breakStatesMap.get(statement));
                        varState.distinct();
                    }
                }
                child.accept(this);
            }
            varState.mergeWith(states);
            breakStatesMap.remove(statement);
        }
//        TODO: Add GuardedBy and GuardedByNegation edges
    }

    @Override
    public void visitTryStatement(PsiTryStatement statement) {
//        System.out.print("visitTryStatement\r");
        VarState beforeTryState = varState.copy();
        acceptIfNotNull(statement.getTryBlock());
        VarState afterTryState = varState.copy();

        varState = beforeTryState;
        Collection<VarState> states = new ArrayList<>();
        states.add(varState.copy());
        for (PsiCatchSection catchSection : statement.getCatchSections()) {
            catchSection.accept(this);
            states.add(varState.copy());
            varState.mergeWith(states);
        }
        varState.mergeWith(afterTryState);

        acceptIfNotNull(statement.getFinallyBlock());
    }

    @Override
    public void visitWhileStatement(PsiWhileStatement statement) {
//        System.out.print("visitWhileStatement\r");
        acceptIfNotNull(statement.getCondition());

        VarState noBodyExecutionState = varState.copy();

        // Unfold twice
        acceptIfNotNull(statement.getBody());
        updateVarStateWithStatementState(statement, continueStatesMap);
        acceptIfNotNull(statement.getCondition());
        acceptIfNotNull(statement.getBody());
        updateVarStateWithStatementState(statement, continueStatesMap);
        updateVarStateWithStatementState(statement, breakStatesMap);

        varState.mergeWith(noBodyExecutionState);
        continueStatesMap.remove(statement);
        breakStatesMap.remove(statement);

//        Add GuardedBy edges
        List<PsiVariable> includeOnly = findVarIdentifiersUnderNode(statement.getCondition())
                .stream()
                .map(DeprecatedPsiUtils::findVariableDeclaration)
                .collect(Collectors.toList());
        addEdgesFromNodeIdsToId(statement.getBody(), statement.getCondition(), GUARDED_BY, includeOnly);
    }

    @Override
    public void visitDoWhileStatement(PsiDoWhileStatement statement) {
//        System.out.print("visitDoWhileStatement\r");
        // Unfold twice
        acceptIfNotNull(statement.getBody());
        updateVarStateWithStatementState(statement, continueStatesMap);
        acceptIfNotNull(statement.getCondition());
        acceptIfNotNull(statement.getBody());
        updateVarStateWithStatementState(statement, continueStatesMap);
        acceptIfNotNull(statement.getCondition());
        updateVarStateWithStatementState(statement, breakStatesMap);

        continueStatesMap.remove(statement);
        breakStatesMap.remove(statement);

        //        Add GuardedBy edges
        List<PsiVariable> includeOnly = findVarIdentifiersUnderNode(statement.getCondition())
                .stream()
                .map(DeprecatedPsiUtils::findVariableDeclaration)
                .collect(Collectors.toList());
        addEdgesFromNodeIdsToId(statement.getBody(), statement.getCondition(), GUARDED_BY, includeOnly);
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
//        System.out.print("visitForStatement\r");
        acceptIfNotNull(statement.getInitialization());
        acceptIfNotNull(statement.getCondition());

        VarState noBodyExecutionState = varState.copy();

        for (int i = 0; i < 2; i++) {  // Unfold twice
            acceptIfNotNull(statement.getBody());
            updateVarStateWithStatementState(statement, continueStatesMap);
            acceptIfNotNull(statement.getUpdate());
            acceptIfNotNull(statement.getCondition());
        }
        updateVarStateWithStatementState(statement, breakStatesMap);

        varState.mergeWith(noBodyExecutionState);

        continueStatesMap.remove(statement);
        breakStatesMap.remove(statement);

        //        Add GuardedBy edges
        List<PsiVariable> includeOnly = findVarIdentifiersUnderNode(statement.getCondition())
                .stream()
                .map(DeprecatedPsiUtils::findVariableDeclaration)
                .collect(Collectors.toList());
        addEdgesFromNodeIdsToId(statement.getBody(), statement.getCondition(), GUARDED_BY, includeOnly);
    }

    @Override
    public void visitForeachStatement(PsiForeachStatement statement) {
//        System.out.print("visitForeachStatement\r");
        acceptIfNotNull(statement.getIteratedValue());
        VarState noBodyExecutionState = varState.copy();
        for (int i = 0; i < 2; i++) {  // Unfold twice
            acceptIfNotNull(statement.getIterationParameter());
            acceptIfNotNull(statement.getBody());
            updateVarStateWithStatementState(statement, continueStatesMap);
        }
        updateVarStateWithStatementState(statement, breakStatesMap);
        varState.mergeWith(noBodyExecutionState);
        continueStatesMap.remove(statement);
        breakStatesMap.remove(statement);
    }

    private void addState(PsiStatement statement, VarState state, IdentityHashMap<PsiStatement, List<VarState>> toMap) {
        toMap.putIfAbsent(statement, new ArrayList<>());
        toMap.get(statement).add(state);
    }

    private void updateVarStateWithStatementState(PsiStatement statement, IdentityHashMap<PsiStatement, List<VarState>> continueStatesMap) {
        continueStatesMap.computeIfPresent(statement, (k, v) -> {
            varState.mergeWith(v);
            return new ArrayList<>();
        });
    }

    private void acceptIfNotNull(PsiElement element) {
        if (element == null) return;
        element.accept(this);
    }
}