package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class AStar extends Plan {

    public static AStar forObject(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        return new AStar(goal, ship, space);
    }

    private AStar(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        super(goal, ship, space);
        Graph<Node> searchGraph = createSearchGraph();
        steps = search(searchGraph);
    }

    /**
     * A* search returns null for failure otherwise a list of positions to travel through
     * Similar to GBFS except it considers the cost of the path to reach a node.
     * Adds all neighbor nodes to a priority queue ordered by their f(n) values starting
     * with the root and continuing with nodes popped off the queue until the goal node is found.
     * The value of g(n) can change and cause a node in the priority queue to be replaced.
     * <p>
     * <p>
     * f(n) = g(n) + h(n)
     *
     * @param searchGraph The graph of positions to search through
     * @return A list of positions to travel through to reach the target
     * @see #heuristicCostEstimate(Position, Position) for h(n)
     */
    @Override
    List<Position> search(Graph<Node> searchGraph) {
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getCost));
        frontier.add(searchGraph.getStart());

        while (!frontier.isEmpty()) {
            Node node = frontier.poll();
            if (node == null) {
                System.out.println("Node null somehow");
                return null;
            }
            Position nodePosition = node.getPosition();
            if (nodePosition.equalsLocationOnly(searchGraph.getEnd().getPosition())) {
                return reconstructPath(node);
            }

            node.setExplored(true);
            for (Node neighbor : searchGraph.adjacentNodes(node)) {
                if (neighbor.isExplored()) {
                    continue;
                }

                if (!frontier.contains(neighbor)) {
                    frontier.add(neighbor);
                }

                double distanceToNeighbor = space.findShortestDistance(nodePosition, neighbor.getPosition());
                double tentativeGScore = node.getCurrentPathCost() + distanceToNeighbor;

                if (tentativeGScore >= neighbor.getCurrentPathCost()) {
                    continue;
                }
                double distanceToGoal = heuristicCostEstimate(neighbor.getPosition(), searchGraph.getEnd().getPosition());
                neighbor.setPrevious(node);
                neighbor.setCurrentPathCost(tentativeGScore);
                neighbor.setDistanceToGoal(distanceToGoal);
            }
        }
        return null; // failure (maybe we should get a new target?)
    }

    @Override
    double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }
}