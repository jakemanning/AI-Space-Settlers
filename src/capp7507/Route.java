package capp7507;

import spacesettlers.graphics.*;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * An abstract Route, using a heuristic and routing algorithm to determine the best route from a ship to a goal.
 */
public abstract class Route {
    private int nextStep = 0;
    private Position initialShipPosition;
    private static final int N_DISTANCES = 10;
    private static final int N_ANGLES = 11;
    private Set<CircleGraphics> searchGraphGraphics = new HashSet<>();
    List<Position> steps;
    private Ship ship;
    private AbstractObject goal;
    Toroidal2DPhysics space;
    private static Set<Position> baseCandidates;

    Route(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        this.ship = ship;
        this.goal = goal;
        this.space = space;
        this.initialShipPosition = ship.getPosition();
    }

    /**
     * The current step of the plan
     *
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
     *
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
     *
     * @return true if our plan is finished (or search failed)
     */
    public boolean isDone() {
        return getStep() == null;
    }

    /**
     * Creates a search graph by fanning out multiple {@link RouteNode} objects. We did this by calculating the distance
     * from the ship to the goal, and chose {@value N_DISTANCES} as the number of points to divide this distance up by,
     * forming a line of nodes. Next we created a semi-circle by replicating this line multiple times, choosing {@value N_ANGLES} angles.
     * There are also additional nodes placed around the target.
     * We connect all of the nodes only if there's no obstacle in the way, and only if the distance between them is smaller than
     * half of our goal distance. Finally, we fill the {@link Graph} with these {@link RouteNode}s iff there is no obstacle nearby.
     *
     * @return the completed {@link Graph}
     */
    Graph<RouteNode> createSearchGraph() {
        Position goalPosition = goal.getPosition();
        RouteNode rootNode = new RouteNode(initialShipPosition, 0, heuristicCostEstimate(initialShipPosition, goalPosition));
        RouteNode goalNode = new RouteNode(goalPosition);

        Set<RouteNode> nodes = new HashSet<>();
        Map<RouteNode, Set<RouteNode>> edges = new HashMap<>();

        nodes.add(rootNode);
        nodes.add(goalNode);

        Set<AbstractObject> obstructions = SpaceSearchUtil.getObstructions(space, ship);
        obstructions.remove(goal);

        if (baseCandidates == null) {
            baseCandidates = new HashSet<>(100);
            int widthDiff = space.getWidth() / 10;
            int heightDiff = space.getHeight() / 10;
            for (int x = 0; x < space.getWidth(); x += widthDiff) {
                for (int y = 0; y < space.getHeight(); y += heightDiff) {
                    Position position = new Position(x, y);
                    baseCandidates.add(position);
                }
            }
        }

        Set<Position> candidates = new HashSet<>(baseCandidates);

        // add a few more nodes around the goal
//        int nNodesAroundGoal = N_ANGLES * 2;
//        for (int i = 0; i < nNodesAroundGoal; i++) {
//            double angleDiff = 2 * i * Math.PI / nNodesAroundGoal;
//            double magnitude = space.findShortestDistance(initialShipPosition, goalPosition) / N_DISTANCES;
//            Vector2D diff = Vector2D.fromAngle(angleDiff, magnitude);
//            Vector2D goalVector = new Vector2D(goalPosition);
//            Vector2D goalPlusDiffVector = goalVector.add(diff);
//            Position goalPlusDiff = new Position(goalPlusDiffVector);
//            candidates.add(goalPlusDiff);
//        }

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
                RouteNode node = new RouteNode(position);
                nodes.add(node);
            }
        }

        // connect neighbors
        for (RouteNode node1 : nodes) {
            HashSet<RouteNode> neighbors = new HashSet<>();
            for (RouteNode node2 : nodes) {
                // only connect nodes if they are not equal and the path is clear between them
                if (node1 != node2 && space.isPathClearOfObstructions(node1.getPosition(),
                        node2.getPosition(), obstructions, ship.getRadius() * 2)) {
                    neighbors.add(node2);
                }
            }
            edges.put(node1, neighbors);
        }

        nodes.forEach(node -> searchGraphGraphics.add(new CircleGraphics(2, Color.GRAY, node.getPosition())));

        return new Graph<>(nodes, edges, rootNode, goalNode);
    }

    /**
     * Search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     */
    abstract List<Position> search(Graph<RouteNode> searchGraph);

    /**
     * An estimate of the cost to get from the start to end position
     *
     * @param start The beginning position
     * @param end   The ending position
     * @return an estimate of the cost to get from start to end position, based on the {@link Route}'s implementation
     */
    abstract double heuristicCostEstimate(Position start, Position end);

    /**
     * Work backwards, adding the nodes to the path
     *
     * @param current The node to work backwards from
     * @return The correctly ordered a* search path from start to current node
     */
    List<Position> reconstructPath(RouteNode current) {
        List<Position> path = new ArrayList<>();
        path.add(current.getPosition());
        RouteNode previous = current.getPrevious();
        while (previous != null) {
            path.add(previous.getPosition());
            previous = previous.getPrevious();
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * For debugging purposes, graphics of our current plan gets drawn
     *
     * @return the set of graphics our plan created
     */
    public Set<SpacewarGraphics> getGraphics() {
        Set<SpacewarGraphics> results = new HashSet<>();
        if (isDone()) {
            return results;
        }

        Position previous = steps.get(0);
        Position goal = this.goal.getPosition();
        results.add(new StarGraphics(4, Color.PINK, previous));
        for (int i = 1; i < steps.size(); ++i) {
            Position step = steps.get(i);
            if (step.equalsLocationOnly(goal)) {
                results.add(new TargetGraphics(8, step));
            } else if (step.equalsLocationOnly(getStep())) {
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
     *
     * @return our target that we are trying to reach
     */
    public AbstractObject getGoal() {
        return goal;
    }
}