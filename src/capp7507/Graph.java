package capp7507;

import java.util.Map;
import java.util.Set;

/**
 * A class to represent our search graph for both A* and GBFS
 *
 * @param <T> The type of the nodes in the graph
 */
public class Graph<T> {
    private final Set<T> nodes;
    private final Map<T, Set<T>> edges;
    private final T start;
    private final T end;

    Graph(Set<T> nodes, Map<T, Set<T>> edges, T start, T end) {
        this.nodes = nodes;
        this.edges = edges;
        this.start = start;
        this.end = end;
    }

    /**
     * The nodes in the graph
     *
     * @return A set of the nodes in the graph
     */
    public Set<T> nodes() {
        return nodes;
    }

    /**
     * The edges in the graph
     * There exists an edge from a to b if b exists in the set returned from edges().get(a)
     *
     * @return A map from nodes to sets of nodes where every node in the set is adjacent to the corresponding node
     */
    public Map<T, Set<T>> edges() {
        return edges;
    }

    /**
     * The nodes adjacent to a given parent node
     *
     * @param parent The node whose adjacent nodes should be returned
     * @return A set of nodes that are adjacent to parent
     */
    Set<T> adjacentNodes(T parent) {
        return edges.get(parent);
    }

    /**
     * The start or root node of the graph
     * Typically, the location of the ship
     *
     * @return The start node
     */
    public T getStart() {
        return start;
    }

    /**
     * The goal node in the graph
     * Typically, the location of the target object
     *
     * @return The goal node
     */
    public T getEnd() {
        return end;
    }
}