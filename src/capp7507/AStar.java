package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.*;

public class AStar extends Plan {

    public static AStar forObject(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        return new AStar(goal, ship, space);
    }

    private AStar(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        super(goal, ship, space);
        Graph<Node> searchGraph = createSearchGraph();
        Position shipPos = ship.getPosition();
        Position goalPos = JakeTeamClient.interceptPosition(space, goal.getPosition(), shipPos);
        steps = search(searchGraph, shipPos, goalPos);
    }

    /**
     * A* search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    @Override
    List<Position> search(Graph<Node> searchGraph, Position start, Position goal) {
        Node root = new Node(start, 0, heuristicCostEstimate(start, goal));
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getCost));
        frontier.add(root);

        while(!frontier.isEmpty()) {
            Node node = frontier.poll();
            Position nodePosition = node.getPosition();
            if(nodePosition.equalsLocationOnly(goal)) {
                return reconstructPath(node);
            }

            node.setExplored(true);
            for(Node neighbor : searchGraph.adjacentNodes(node)) {
                if(neighbor.isExplored()) {
                    continue;
                }

                if(!frontier.contains(neighbor)) {
                    frontier.add(neighbor);
                }

                double distanceToNeighbor = space.findShortestDistance(nodePosition, neighbor.getPosition());
                double tentativeGScore = node.getCurrentPathCost() + distanceToNeighbor;

                if(tentativeGScore >= neighbor.getCurrentPathCost()) {
                    continue;
                }
                double distanceToGoal = heuristicCostEstimate(neighbor.getPosition(), goal);
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
