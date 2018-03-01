package capp7507;

import spacesettlers.graphics.*;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * An abstract Plan, using a heuristic and routing algorithm to determine the best route from a ship to a goal.
 */
public abstract class Plan {
    private int nextStep = 0;
    private Position initialShipPosition;
    private static final int N_DISTANCES = 10;
    private static final int N_ANGLES = 11;
    private Set<CircleGraphics> searchGraphGraphics = new HashSet<>();
    List<Position> steps;
    private Ship ship;
    private AbstractObject goal;
    Toroidal2DPhysics space;

    Plan(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        this.ship = ship;
        this.goal = goal;
        this.space = space;
        this.initialShipPosition = ship.getPosition();
    }

    /**
     * The current step of the plan
     * @return the current step of the plan, null if the plan is finished (or search failed)
     */
    public Position getStep() {
        if (steps == null || nextStep >= steps.size()) {
            return null;
        }
        return steps.get(nextStep);
    }

    /**
     * The next step of the plan, if exists
     * @return the next step of the plan, null if plan is finished (or search failed)
     */
    public Position getNextStep() {
        if (steps == null || nextStep + 1 >= steps.size()) {
            return null;
        }
        return steps.get(nextStep + 1);
    }

    /**
     * Increments the step counter once we have completed a step
     */
    public void completeStep() {
        if (steps == null) {
            return;
        }
        nextStep += 1;
    }

    /**
     * Whether or not we have completed our plan
     * @return true if our plan is finished (or search failed)
     */
    public boolean isDone() {
        return getStep() == null;
    }

    /**
     * Creates a search graph by fanning out multiple {@link Node} objects. We did this by calculating the distance
     * from the ship to the goal, and chose {@value N_DISTANCES} as the number of points to divide this distance up by,
     * forming a line of nodes. Next we created a semi-circle by replicating this line multiple times, choosing {@value N_ANGLES} angles.
     * We connect all of the nodes only if there's no obstacle in the way, and only if the distance between them is smaller than
     * half of our goal distance. Finally, we fill the {@link Graph} with these {@link Node}s iff there is no obstacle nearby.
     * @return the completed {@link Graph}
     */
    Graph<Node> createSearchGraph() {
        Position goalPosition = JakeTeamClient.interceptPosition(space, goal.getPosition(), initialShipPosition);
        Node root = new Node(initialShipPosition, 0, heuristicCostEstimate(initialShipPosition, goalPosition));
        Node goal = new Node(goalPosition);

        Set<Node> nodes = new HashSet<>();
        Map<Node, Set<Node>> edges = new HashMap<>();

        nodes.add(root);
        nodes.add(goal);

        Set<AbstractObject> obstructions = obstructions();

        Set<Position> candidates = new HashSet<>();

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
                candidates.add(position);
            }
        }

        // add a few more nodes around the goal
        int nNodesAroundGoal = N_ANGLES * 2;
        for (int i = 0; i < nNodesAroundGoal; i++) {
            double angleDiff = 2 * i * Math.PI / nNodesAroundGoal;
            double magnitude = space.findShortestDistance(initialShipPosition, goalPosition) / N_DISTANCES;
            Vector2D diff = Vector2D.fromAngle(angleDiff, magnitude);
            Vector2D goalVector = new Vector2D(goalPosition);
            Vector2D goalPlusDiffVector = goalVector.add(diff);
            Position goalPlusDiff = new Position(goalPlusDiffVector);
            candidates.add(goalPlusDiff);
        }

        // determine whether there is an obstacle nearby for all positions
        for (Position position : candidates) {
            boolean isObstructionNear = false;
            for (AbstractObject obstruction : obstructions) {
                double obstructionDistance = space.findShortestDistance(position, obstruction.getPosition());
                if (obstructionDistance <= ship.getRadius() * 2) {
                    isObstructionNear = true;
                    break;
                }
            }

            if (!isObstructionNear) {
                Node node = new Node(position);
                nodes.add(node);
            }
        }

        // connect neighbors
        for (Node node1 : nodes) {
            HashSet<Node> neighbors = new HashSet<>();
            for (Node node2 : nodes) {
                // only connect nodes if they are not equal, the path is clear between them,
                // and they are a short distance apart
                double shortDistance = space.findShortestDistance(initialShipPosition, goalPosition) / 2;
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

        return new Graph<>(nodes, edges, root, goal);
    }

    /**
     * The possible obstructions we need to worry about
     * @return any {@link AbstractObject}'s we need to avoid
     */
    private Set<AbstractObject> obstructions() {
        return JakeTeamClient.getObstructions(space, ship);
    }

    /**
     * Search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    abstract List<Position> search(Graph<Node> searchGraph);

    /**
     * An estimate of the cost to get from the start to end position
     * @param start The beginning position
     * @param end The ending position
     * @return an estimate of the cost to get from start to end position, based on the {@link Plan}'s implementation
     */
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

    /**
     * For debugging purposes, graphics of our current plan gets drawn
     * @return the set of graphics our plan created
     */
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

    /**
     * Our goal to reach
     * @return our target that we are trying to reach
     */
    public AbstractObject getGoal() {
        return goal;
    }
}
