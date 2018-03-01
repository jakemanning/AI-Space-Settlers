package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class GBFS extends Plan {

    public static GBFS forObject(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        return new GBFS(goal, ship, space);
    }

    private GBFS(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        super(goal, ship, space);
        Graph<Node> searchGraph = createSearchGraph();
        steps = search(searchGraph);
    }

    /**
     * GBFS search returns null for failure otherwise a list of positions to travel through
     * Similar to A* except it does not consider the cost of the path to reach a node.
     * Adds all neighbor nodes to a priority queue starting with the root and continuing
     * with nodes popped off the queue until the goal node is found
     *
     * f(n) = h(n)
     * @see #heuristicCostEstimate(Position, Position) for h(n)
     *
     * @param searchGraph The graph of positions to search through
     * @return A list of positions to travel through to reach the target
     */
    @Override
    List<Position> search(Graph<Node> searchGraph) {
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getDistanceToGoal));
        frontier.add(searchGraph.getStart());

        while(!frontier.isEmpty()) {
            Node node = frontier.poll();
            Position nodePosition = node.getPosition();
            if(nodePosition.equalsLocationOnly(searchGraph.getEnd().getPosition())) {
                return reconstructPath(node);
            }

            node.setExplored(true);
            for(Node neighbor : searchGraph.adjacentNodes(node)) {
                if(neighbor.isExplored()) {
                    continue;
                }

                if(!frontier.contains(neighbor)) {
                    double distanceToGoal = heuristicCostEstimate(neighbor.getPosition(), searchGraph.getEnd().getPosition());
                    neighbor.setDistanceToGoal(distanceToGoal);
                    frontier.add(neighbor);
                }

                neighbor.setPrevious(node);
            }
        }
        return null; // failure (maybe we should getRadius a new target)
    }

    @Override
    double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }
}
