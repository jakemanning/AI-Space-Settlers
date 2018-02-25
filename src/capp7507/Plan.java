package capp7507;

import spacesettlers.graphics.LineGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.StarGraphics;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Plan {
    private Ship ship;
    private AbstractObject goal;
    private Toroidal2DPhysics space;
    private List<Position> steps;
    private int nextStep = 0;

    private Plan(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        this.goal = goal;
        this.ship = ship;
        this.space = space;

        Graph<Node> searchGraph = createSearchGraph();
        steps = myAttempt(searchGraph);
    }

    public static Plan forObject(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        return new Plan(goal, ship, space);
    }

    public Position nextStep() {
        if (steps == null) {
            return null;
        }
        Position step = steps.get(nextStep);
        nextStep++;
        return step;
    }

    public boolean isDone() {
        return steps == null || steps.size() <= nextStep;
    }

    private Graph<Node> createSearchGraph() {
        Position shipPosition = ship.getPosition();
        Position goalPosition = BryanTeamClient.interceptPosition(space, goal.getPosition(), shipPosition);
        Node root = new Node(shipPosition, 0, heuristicCostEstimate(shipPosition, goalPosition));
        Node goal = new Node(goalPosition);

        Set<Node> nodes = new HashSet<>();
        Map<Node, Set<Node>> edges = new HashMap<>();

        nodes.add(root);
        nodes.add(goal);

        int nAngles = 11, nDistances = 10;
        for (int angleDiv = nAngles / -2; angleDiv < nAngles - 5; angleDiv++) {
            double angleDiff = angleDiv * Math.PI / (nAngles - 1);
            for (int distanceDiv = 1; distanceDiv <= nDistances; distanceDiv++) {
                Vector2D goalVector = space.findShortestDistanceVector(root.getPosition(), goalPosition);
                double totalDistance = goalVector.getMagnitude();
                double distance = distanceDiv * totalDistance / nDistances;

                double angle = goalVector.getAngle() + angleDiff;
                Vector2D rootVector = new Vector2D(root.getPosition());
                Vector2D positionVector = rootVector.add(Vector2D.fromAngle(angle, distance));
                Position position = new Position(positionVector);
                Node node = new Node(position);
                nodes.add(node);
            }
        }
        Set<AbstractObject> obstructions = obstructions();

        for (Node node1 : nodes) {
            HashSet<Node> neighbors = new HashSet<>();
            for (Node node2 : nodes) {
                if (node1 != node2 && space.isPathClearOfObstructions(node1.getPosition(), node2.getPosition(), obstructions, ship.getRadius())) {
                    neighbors.add(node2);
                }
            }
            edges.put(node1, neighbors);
        }

        return new Graph<>(nodes, edges);
    }

    private Set<AbstractObject> obstructions() {
        Set<AbstractObject> obstructions = new HashSet<>();
        Set<Ship> otherShips = space.getShips().stream()
                .filter(ship -> !ship.getId().equals(this.ship.getId()))
                .collect(Collectors.toSet());
        Set<Asteroid> badAsteroids = space.getAsteroids().stream()
                .filter(asteroid -> !asteroid.isMineable())
                .collect(Collectors.toSet());
        Set<Base> bases = space.getBases().stream()
                .filter(base -> !base.equals(goal))
                .collect(Collectors.toSet());
        obstructions.addAll(otherShips);
        obstructions.addAll(badAsteroids);
        obstructions.addAll(bases);
        return obstructions;
    }

    /**
     * A* search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    private List<Position> myAttempt(Graph<Node> searchGraph) {
        Position shipPos = ship.getPosition();
        Position goalPos = BryanTeamClient.interceptPosition(space, goal.getPosition(), shipPos);
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

    protected double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }

    public Set<SpacewarGraphics> getGraphics() {
        Set<SpacewarGraphics> results = new HashSet<>();
        if (steps == null || steps.size() == 0) {
            return results;
        }

        Position previous = steps.get(0);
        for (Position current : steps) {
            results.add(new StarGraphics(4, Color.PINK, current)); // Seems to make sense for a-STAR ;)
            LineGraphics line = new LineGraphics(previous, current, space.findShortestDistanceVector(previous, current));
            previous = current;
            results.add(line);
        }

        return results;
    }

    public AbstractObject getGoal() {
        return goal;
    }
}
