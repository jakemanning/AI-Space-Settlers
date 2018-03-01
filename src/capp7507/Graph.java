package capp7507;

import java.util.Map;
import java.util.Set;

public class Graph<T> {
    private final Set<T> nodes;
    private final Map<T, Set<T>> edges;
    private final Node start;
    private final Node end;

    Graph(Set<T> nodes, Map<T, Set<T>> edges, Node start, Node end) {
        this.nodes = nodes;
        this.edges = edges;
        this.start = start;
        this.end = end;
    }

    public Set<T> nodes() {
        return nodes;
    }

    public Map<T, Set<T>> edges() {
        return edges;
    }

    public Set<T> adjacentNodes(T parent) {
        return edges.get(parent);
    }

    public Node getStart() {
        return start;
    }

    public Node getEnd() {
        return end;
    }
}
