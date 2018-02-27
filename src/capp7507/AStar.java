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
        this.goal = goal;
        this.ship = ship;
        this.space = space;

        Graph<Node> searchGraph = createSearchGraph();
        steps = search(searchGraph);
    }

    /**
     * A* search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    @Override
    List<Position> search(Graph<Node> searchGraph) {
        Position shipPos = ship.getPosition();
        Position goalPos = JakeTeamClient.interceptPosition(space, goal.getPosition(), shipPos);
        Node root = new Node(shipPos, 0, heuristicCostEstimate(shipPos, goalPos));
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getCost));
        frontier.add(root);
        Node node;

        while(!frontier.isEmpty()) {
            node = frontier.poll();
            Position nodePosition = node.getPosition();
            if(nodePosition.equalsLocationOnly(goalPos)) {
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
                double distanceToGoal = heuristicCostEstimate(neighbor.getPosition(), goalPos);
                neighbor.setPrevious(node);
                neighbor.setCurrentPathCost(tentativeGScore);
                neighbor.setDistanceToGoal(distanceToGoal);
            }
        }
        return null; // failure (maybe we should get a new target?)
    }

    /**
     * Work backwards, adding the nodes to the path
     * @param current The node to work backwards from
     * @return The correctly ordered a* search path from start to current node
     */
    private List<Position> reconstructPath(Node current) {
        List<Position> path = new ArrayList<>();
        path.add(current.getPosition());
        Node previous = current.getPrevious();
        while(previous != null) {
            path.add(previous.getPosition());
            previous = previous.getPrevious();
        }
        Collections.reverse(path);
        return path;
    }

    @Override
    double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }
}
