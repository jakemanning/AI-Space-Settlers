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
        this.goal = goal;
        this.ship = ship;
        this.space = space;

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
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getDistanceToGoal));
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

                double distanceToGoal = heuristicCostEstimate(neighbor.getPosition(), goal);
                neighbor.setPrevious(node);
                neighbor.setDistanceToGoal(distanceToGoal);
            }
        }
        return null; // failure (maybe we should getRadius a new target)
    }

    @Override
    double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }
}
