package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.*;
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

        Graph<Position> searchGraph = createSearchGraph();
        steps = search(searchGraph);
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

    private Graph<Position> createSearchGraph() {
        Position root = ship.getPosition();
        Position goalPosition = BryanTeamClient.interceptPosition(space, goal.getPosition(), root);
        Set<Position> nodes = new HashSet<>();
        Map<Position, Set<Position>> edges = new HashMap<>();

        nodes.add(root);
        nodes.add(goalPosition);

        int nAngles = 11, nDistances = 10;
        for (int angleDiv = nAngles / -2; angleDiv < nAngles - 5; angleDiv++) {
            double angleDiff = angleDiv * Math.PI / (nAngles - 1);
            for (int distanceDiv = 1; distanceDiv <= nDistances; distanceDiv++) {
                Vector2D goalVector = space.findShortestDistanceVector(root, goalPosition);
                double totalDistance = goalVector.getMagnitude();
                double distance = distanceDiv * totalDistance / nDistances;

                double angle = goalVector.getAngle() + angleDiff;
                Vector2D rootVector = new Vector2D(root);
                Vector2D positionVector = rootVector.add(Vector2D.fromAngle(angle, distance));
                Position position = new Position(positionVector);
                nodes.add(position);
            }
        }

        for (Position node1 : nodes) {
            HashSet<Position> neighbors = new HashSet<>();
            for (Position node2 : nodes) {
                if (space.isPathClearOfObstructions(node1, node2, obstructions(), ship.getRadius())) {
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
     */
    private List<Position> search(Graph<Position> searchGraph) {
        Position shipPos = ship.getPosition();
        Position goalPos = this.goal.getPosition();
        Node root = new Node(shipPos, 0, space.findShortestDistance(shipPos, goalPos));
        List<Node> path = new ArrayList<>(searchGraph.nodes().size());
        Node node;
        PriorityQueue<Node> frontier = new PriorityQueue<>(Comparator.comparingDouble(Node::getCost));
        frontier.add(root);
        for (int i = 0; i < 1000; i++) {
            if (frontier.isEmpty()) {
                return null;
            }
            node = frontier.poll();

            // trying to keep the path, probably not right
            Node parent = root;
            Position nodePosition = node.getPosition();
            while (path.size() > 1) {
                parent = path.get(path.size() - 1);
                if (searchGraph.adjacentNodes(parent.getPosition()).contains(nodePosition)) {
                    break;
                } else {
                    path.remove(path.size() - 1);
                }
            }
            node.setCurrentPathCost(parent.getCurrentPathCost() + space.findShortestDistance(parent.getPosition(), nodePosition));
            path.add(node);

            if (nodePosition.equalsLocationOnly(goalPos)) {
                // we're at the goal, return the path
                return path.stream()
                        .map(Node::getPosition)
                        .collect(Collectors.toList());
            }

            node.setExplored(true);
            for (Position childPos : searchGraph.adjacentNodes(nodePosition)) {
                double distanceToChild = space.findShortestDistance(nodePosition, childPos);
                double distanceToGoal = space.findShortestDistance(childPos, goalPos);
                Node child = new Node(childPos, node.getCurrentPathCost() + distanceToChild, distanceToGoal);
                if (!child.isExplored() && !frontier.contains(child)) {
                    frontier.add(child);
                } else if (frontier.contains(child)) {
                    // replace if path cost is lower now. How????
                    boolean replace = false;
                    for (Node possibleChild : frontier) {
                        if (possibleChild.equals(child) && possibleChild.getCost() > child.getCost()) {
                            replace = true;
                            break;
                        }
                    }
                    if (replace) {
                        frontier.remove(child);
                        frontier.add(child);
                    }
                }
            }
        }
        return null;
    }

    public AbstractObject getGoal() {
        return goal;
    }
}
