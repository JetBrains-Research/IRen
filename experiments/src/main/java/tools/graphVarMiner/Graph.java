package tools.graphVarMiner;

import org.apache.commons.lang.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;

import static tools.graphVarMiner.MapUtils.copyMap;

public class Graph<T> {
    protected List<T> NodeLabels = new ArrayList<T>();
    protected Map<T, Integer> nodeToIdx = new IdentityHashMap<>();
    protected Map<String, Map<Integer, Set<Integer>>> edges = new HashMap<>();
    private final HashSet<T> visitedNodes = new HashSet<>();

    public int getNodeId(T node) {
        return nodeToIdx.computeIfAbsent(node, n -> {
            int idx = NodeLabels.size();
            NodeLabels.add(n);
            nodeToIdx.put(n, idx);
            return idx;
        });
    }

    public boolean containsNode(T node) {
        return nodeToIdx.containsKey(node);
    }

    public void addEdge(@Nullable T from, @Nullable T to, @NotNull String edgeType, boolean withReversed) {
        if (from == null || to == null) return;
        addEdge(from, to, edgeType);
        if (withReversed) addEdge(to, from, reverseEdgeType(edgeType));
    }

    private void addEdge(@NotNull T from, @NotNull T to, @NotNull String edgeType) {
        int fromIdx = getNodeId(from);
        int toIdx = getNodeId(to);
        addEdge(fromIdx, toIdx, edgeType);
    }

    private void addEdge(int fromIdx, int toIdx, @NotNull String edgeType) {
        if (!edges.containsKey(edgeType)) {
            edges.put(edgeType, new HashMap<>());
        }

        Map<Integer, Set<Integer>> outEdges = edges.get(edgeType);
        if (!outEdges.containsKey(fromIdx)) {
            outEdges.put(fromIdx, new HashSet<>());
        }
        Set<Integer> targetNodes = outEdges.get(fromIdx);
        targetNodes.add(toIdx);
    }

    public void removeEdge(@Nullable T from, @Nullable T to, @NotNull String edgeType, boolean withReversed) {
        if (from == null || to == null) return;
        removeEdge(from, to, edgeType);
        if (withReversed) removeEdge(to, from, reverseEdgeType(edgeType));
    }

    private void removeEdge(@NotNull T from, @NotNull T to, @NotNull String edgeType) {
        if (!edges.containsKey(edgeType)) return;
        Integer fromIdx = nodeToIdx.get(from);
        Integer toIdx = nodeToIdx.get(to);
        if (fromIdx == null || toIdx == null) return;
        removeEdge(fromIdx, toIdx, edgeType);
    }

    private void removeEdge(int fromIdx, int toIdx, @NotNull String edgeType) {
        Map<Integer, Set<Integer>> outEdges = edges.get(edgeType);
        if (!outEdges.containsKey(fromIdx)) return;
        Set<Integer> targetNodes = outEdges.get(fromIdx);
        targetNodes.remove(toIdx);
        if (targetNodes.size() == 0) {
            outEdges.remove(fromIdx);
        }
    }

    private static String reverseEdgeType(String edgeType) {
        if (edgeType.startsWith("reversed")) {
            return edgeType.substring(8);
        }
        return "reversed" + edgeType;
    }

    public @Nullable T getFirstChild(@Nullable T node, @NotNull String edgeType) {
        if (node == null || !edges.containsKey(edgeType)) return null;
        Map<Integer, Set<Integer>> outEdges = edges.get(edgeType);
        Integer nodeIdx = nodeToIdx.get(node);
        if (!outEdges.containsKey(nodeIdx)) return null;
        Integer childIdx = outEdges.get(nodeIdx).stream().findFirst().orElse(null);
        return childIdx == null ? null : NodeLabels.get(childIdx);
    }

    public String toDot(Function<T, String> nodeLabeler) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < NodeLabels.size(); i++) {
            sb.append(i + " [label=\"" + StringEscapeUtils.escapeJava(nodeLabeler.apply(NodeLabels.get(i))) + "\"];\n");
        }

        for (Entry<String, Map<Integer, Set<Integer>>> edgesOfType : edges.entrySet()) {
            for (Entry<Integer, Set<Integer>> outEdges : edgesOfType.getValue().entrySet()) {
                for (int targetNodeIdx : outEdges.getValue()) {
                    sb.append(outEdges.getKey() + "->" + targetNodeIdx + " [label=\"" + edgesOfType.getKey() + "\"];\n");
                }
            }
        }
        return sb.toString();
    }

    public void copyEdgesFromNode(T node, Graph<T> graph, int depth) {
        if (depth < 1 || this.visitedNodes.contains(node) || !graph.containsNode(node)) return;
        this.visitedNodes.add(node);
        int nodeIdx = graph.nodeToIdx.get(node);
        for (Map.Entry<String, Map<Integer, Set<Integer>>> typeEdges : graph.edges.entrySet()) {
            String edgeType = typeEdges.getKey();
            Map<Integer, Set<Integer>> edgesMap = typeEdges.getValue();
            if (!edgesMap.containsKey(nodeIdx)) continue;
            for (int toIdx : edgesMap.get(nodeIdx)) {
                T toNode = graph.NodeLabels.get(toIdx);
                this.addEdge(node, toNode, edgeType, true);
                this.copyEdgesFromNode(toNode, graph, depth - 1);
            }
        }
    }

    public static class JsonSerializableGraph {
        public Map<Integer, String> NodeLabels = new HashMap<Integer, String>();
        public Map<String, List<Integer[]>> Edges = new HashMap<>();
    }

    public JsonSerializableGraph toJsonSerializableObject(Function<T, String> nodeLabeler) throws IOException {
        JsonSerializableGraph graph = new JsonSerializableGraph();
        for (int i = 0; i < this.NodeLabels.size(); i++) {
            graph.NodeLabels.put(i, nodeLabeler.apply(this.NodeLabels.get(i)));
        }

        for (Entry<String, Map<Integer, Set<Integer>>> edgesOfType : this.edges.entrySet()) {
            List<Integer[]> adjList = new ArrayList<>();
            graph.Edges.put(edgesOfType.getKey(), adjList);
            for (Entry<Integer, Set<Integer>> fromToEdge : edgesOfType.getValue().entrySet()) {
                for (int targetEdge : fromToEdge.getValue()) {
                    Integer[] edge = new Integer[]{fromToEdge.getKey(), targetEdge};
                    adjList.add(edge);
                }
            }
        }
        return graph;
    }

    public Graph<T> shallowCopy(String... edgesToCopy) {
        Graph<T> copy = new Graph<>();
        copy.edges = copyMap(this.edges, edgesToCopy);
        copy.NodeLabels = this.NodeLabels;
        copy.nodeToIdx = this.nodeToIdx;
        return copy;
    }

    public Graph<T> deepCopy() {
        Graph<T> copy = new Graph<>();
        copy.edges = copyMap(this.edges);
        copy.NodeLabels = new ArrayList<T>(this.NodeLabels);
        copy.nodeToIdx = new IdentityHashMap<T, Integer>(this.nodeToIdx);
        return copy;
    }
}