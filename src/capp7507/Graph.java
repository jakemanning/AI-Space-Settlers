package capp7507;

import java.util.Map;
import java.util.Set;

public class Graph<T> {
    private final Set<T> nodes;
    private final Map<T, Set<T>> edges;

    Graph(Set<T> nodes, Map<T, Set<T>> edges) {
        this.nodes = nodes;
        this.edges = edges;
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
}
