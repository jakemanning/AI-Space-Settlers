package capp7507;

import spacesettlers.graphics.*;
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

public abstract class Plan {
    private int nextStep = 0;
    private Position initialShipPosition;
    private static final int N_DISTANCES = 10;
    private static final int N_ANGLES = 11;
    private Set<CircleGraphics> searchGraphGraphics = new HashSet<>();
    List<Position> steps;
    Ship ship;
    AbstractObject goal;
    Toroidal2DPhysics space;

    Plan(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        this.ship = ship;
        this.goal = goal;
        this.space = space;
        this.initialShipPosition = ship.getPosition();

        Graph<Node> searchGraph = createSearchGraph();
        steps = search(searchGraph);
    }

    public Position getStep() {
        if (steps == null || nextStep >= steps.size()) {
            return null;
        }
        return steps.get(nextStep);
    }

    public void completeStep() {
        if (steps == null) {
            return;
        }
        nextStep += 1;
    }

    public boolean isDone() {
        return getStep() == null;
    }

    private Graph<Node> createSearchGraph() {
        Position goalPosition = JakeTeamClient.interceptPosition(space, goal.getPosition(), initialShipPosition);
        Node root = new Node(initialShipPosition, 0, heuristicCostEstimate(initialShipPosition, goalPosition));
        Node goal = new Node(goalPosition);

        Set<Node> nodes = new HashSet<>();
        Map<Node, Set<Node>> edges = new HashMap<>();

        nodes.add(root);
        nodes.add(goal);

        Set<AbstractObject> obstructions = obstructions();

        // loop over n different angles from -180 degrees to 180 degrees
        // where 0 degrees is the angle from the ship to the target
        // and n distances from
        for (int angleDiv = N_ANGLES / -2; angleDiv < N_ANGLES - 5; angleDiv++) {
            double angleDiff = angleDiv * Math.PI / (N_ANGLES - 1);
            for (int distanceDiv = 1; distanceDiv <= N_DISTANCES; distanceDiv++) {
                Vector2D goalVector = space.findShortestDistanceVector(root.getPosition(), goalPosition);
                double totalDistance = goalVector.getMagnitude();
                double distance = distanceDiv * totalDistance / N_DISTANCES;

                double angle = goalVector.getAngle() + angleDiff;
                Vector2D rootVector = new Vector2D(root.getPosition());
                Vector2D positionVector = rootVector.add(Vector2D.fromAngle(angle, distance));
                Position position = new Position(positionVector);

                boolean isObstructionNear = false;
                for(AbstractObject obstruction : obstructions) {
                    double obstructionDistance = space.findShortestDistance(position, obstruction.getPosition());
                    if(obstructionDistance <= ship.getRadius() * 2) {
                        isObstructionNear = true;
                        break;
                    }
                }

                if(!isObstructionNear) {
                    Node node = new Node(position);
                    nodes.add(node);
                }
            }
        }


        for (Node node1 : nodes) {
            HashSet<Node> neighbors = new HashSet<>();
            for (Node node2 : nodes) {
                // only connect nodes if they are not equal, the path is clear between them,
                // and they are a short distance apart
                double shortDistance = space.findShortestDistance(initialShipPosition, goalPosition) / (N_DISTANCES / 2);
                if (node1 != node2
                        && space.isPathClearOfObstructions(node1.getPosition(),
                        node2.getPosition(), obstructions, ship.getRadius())
                        && space.findShortestDistance(node1.getPosition(), node2.getPosition()) < shortDistance) {
                    neighbors.add(node2);
                }
            }
            edges.put(node1, neighbors);
        }

        nodes.forEach(node -> searchGraphGraphics.add(new CircleGraphics(2, Color.GRAY, node.getPosition())));

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
                .filter(base -> !base.getPosition().equalsLocationOnly(goal.getPosition()))
                .collect(Collectors.toSet());
        obstructions.addAll(otherShips);
        obstructions.addAll(badAsteroids);
        obstructions.addAll(bases);
        return obstructions;
    }

    /**
     * Search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    abstract List<Position> search(Graph<Node> searchGraph, Position start, Position goal);

    abstract double heuristicCostEstimate(Position start, Position end);

    /**
     * Work backwards, adding the nodes to the path
     * @param current The node to work backwards from
     * @return The correctly ordered a* search path from start to current node
     */
    List<Position> reconstructPath(Node current) {
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

    public Set<SpacewarGraphics> getGraphics() {
        Set<SpacewarGraphics> results = new HashSet<>();
        if (isDone()) {
            return results;
        }

        Position previous = steps.get(0);
        Position goal = JakeTeamClient.interceptPosition(space, this.goal.getPosition(), ship.getPosition());
        results.add(new StarGraphics(4, Color.PINK, previous));
        for(int i = 1; i < steps.size(); ++i) {
            Position step = steps.get(i);
            if(step.equalsLocationOnly(goal)) {
                results.add(new TargetGraphics(8, step));
            } else if(step.equalsLocationOnly(getStep())) {
                results.add(new StarGraphics(4, Color.MAGENTA, step));
            } else {
                results.add(new StarGraphics(4, Color.PINK, step));
            }

            LineGraphics line = new LineGraphics(previous, step, space.findShortestDistanceVector(previous, step));
            results.add(line);
            previous = step;
        }

        results.addAll(searchGraphGraphics);
        return results;
    }

    public AbstractObject getGoal() {
        return goal;
    }
}
