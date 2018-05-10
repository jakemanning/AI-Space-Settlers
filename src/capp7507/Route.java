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
    private final Ship ship;
    private final int MIN_LOOK_BEHIND_TO_REPLAN = 2;
    private int nextStep = 0;
    private static int LOOK_AHEAD_FROM_SHIP = 5;
    private static int NUM_DIVISIONS_X = 100; // Divisible by 1600
    private static int NUM_DIVISIONS_Y = 60; // Divisible by 1080
    private Set<SpacewarGraphics> searchGraphGraphics = new HashSet<>();
    List<Position> steps;
    private AbstractObject goal;
    private static List<List<RouteNode>> baseCandidates;
    private int LOOK_BEHIND_FROM_GOAL = 15;
    private final ShipRole role;

    Route(AbstractObject goal, Ship ship, ShipRole role) {
        this.goal = goal;
        this.ship = ship;
        this.role = role;
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
    Position getNextStep() {
        if (steps == null || nextStep + 1 >= steps.size()) {
            return null;
        }
        return steps.get(nextStep + 1);
    }

    /**
     * Increments the step counter once we have completed a step
     */
    void completeStep() {
        if (steps == null) {
            return;
        }
        nextStep += 1;
    }

    /**
     *  Determine whether a path between the ship and
     *  the next {@value LOOK_AHEAD_FROM_SHIP} nodes is blocked
     */
    boolean pathBlockedAtStep(Toroidal2DPhysics space, Ship ship, Set<AbstractObject> obstructions) {
        for (int i = nextStep + 1, count = 0; i < steps.size() - 3 && count < LOOK_AHEAD_FROM_SHIP; ++i, ++count) {
            Position currentStep = steps.get(i - 1);
            Position nextStep = steps.get(i);

            if (!space.isPathClearOfObstructions(currentStep, nextStep, obstructions, ship.getRadius())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether or not we have completed our plan
     *
     * @return true if our plan is finished (or search failed)
     */
    boolean isDone() {
        return getStep() == null;
    }

    /**
     * Creates a search graph by creating a grid of {@link RouteNode} objects {@value NUM_DIVISIONS_X} wide,
     * and {@value NUM_DIVISIONS_Y} high. Each {@link RouteNode} will not be included if it has an obstruction in its grid.
     * Finally for each node, we look at the 8 surrounding nodes and connect if no obstacles in those grids.
     * We construct a graph based off of these nodes/connections
     *
     * @return the completed {@link Graph}
     */
    Graph<RouteNode> createSearchGraph(Toroidal2DPhysics space, Ship ship, Position initialShipPosition, Position goalPosition) {
        Set<RouteNode> nodes = new HashSet<>();
        Map<RouteNode, Set<RouteNode>> edges = new HashMap<>();

        Set<AbstractObject> obstructions = SpaceSearchUtil.getObstructions(space, ship, getGoal(space));

        double widthDiff = space.getWidth() / NUM_DIVISIONS_X;
        double heightDiff = space.getHeight() / NUM_DIVISIONS_Y;

        // Initialize RouteNode positions
        if (baseCandidates == null) {
            baseCandidates = new ArrayList<>(NUM_DIVISIONS_X);

            for (double x = 0; x < space.getWidth(); x += widthDiff) {
                List<RouteNode> routeNodes = new ArrayList<>(NUM_DIVISIONS_Y);
                baseCandidates.add(routeNodes);
                for (double y = 0; y < space.getHeight(); y += heightDiff) {
                    Position topLeft = new Position(x, y);
                    Position center = new Position(topLeft.getX() + widthDiff / 2, topLeft.getY() + heightDiff / 2);
                    space.toroidalWrap(center);
                    RouteNode node = new RouteNode(topLeft, center);
                    routeNodes.add(node);
                }
            }
        }

        // Reset all nodes to their default configuration
        for (List<RouteNode> baseCandidate : baseCandidates) {
            for (RouteNode node : baseCandidate) {
                node.resetState();
            }
        }

        // Setup initial node
        RouteNode rootNode = nodeForPosition(space, initialShipPosition);
        rootNode.setCurrentPathCost(0);
        rootNode.setDistanceToGoal(heuristicCostEstimate(space, initialShipPosition, goalPosition));

        // Mark nodes as invalid if contains an obstruction
        for (AbstractObject obstruction : obstructions) {
            if (obstruction.getPosition().equalsLocationOnly(goalPosition)) {
                continue;
            }
            Position obstructionPos = obstruction.getPosition();
            double buffer = obstruction.getRadius();
            if (!obstruction.isMoveable()) {
                buffer += ship.getRadius() * 1.3;
            }
            markArea(space, widthDiff, heightDiff, obstructionPos, buffer, true);
        }

        markArea(space, widthDiff, heightDiff, ship.getPosition(), ship.getRadius() * 1.3, false);
        markArea(space, widthDiff, heightDiff, getGoal(space).getPosition(), getGoal(space).getRadius(), false);

        // Look at the 8 adjacent locations to find a connection
        for (int x = 0; x < baseCandidates.size(); ++x) { // Skip every other row
            for (int y = 0; y < baseCandidates.get(x).size(); ++y) { // Skip every other column
                RouteNode me = baseCandidates.get(x).get(y);
                HashSet<RouteNode> neighbors = new HashSet<>(8);
                edges.put(me, neighbors);
                nodes.add(me);
                if (me.containsObstruction()) {
                    continue;
                }

                RouteNode arr[] = {
                        baseCandidates.get(intRingMod(x - 1, NUM_DIVISIONS_X)).get(intRingMod(y - 1, NUM_DIVISIONS_Y)), // northwest
                        baseCandidates.get(x).get(intRingMod(y - 1, NUM_DIVISIONS_Y)),                                     // north
                        baseCandidates.get(intRingMod(x + 1, NUM_DIVISIONS_X)).get(intRingMod(y - 1, NUM_DIVISIONS_Y)), // northeast
                        baseCandidates.get(intRingMod(x + 1, NUM_DIVISIONS_X)).get(y),                                     // east
                        baseCandidates.get(intRingMod(x + 1, NUM_DIVISIONS_X)).get(intRingMod(y + 1, NUM_DIVISIONS_Y)), // southeast
                        baseCandidates.get(x).get(intRingMod(y + 1, NUM_DIVISIONS_Y)),                                     // south
                        baseCandidates.get(intRingMod(x - 1, NUM_DIVISIONS_X)).get(intRingMod(y + 1, NUM_DIVISIONS_Y)), // southwest
                        baseCandidates.get(intRingMod(x - 1, NUM_DIVISIONS_X)).get(y)                                      // west
                };

                for (RouteNode neighbor : arr) {
                    if (neighbor.containsObstruction()) {
                        continue;
                    }
                    neighbors.add(neighbor);
                }
            }
        }

        RouteNode goalNode = nodeForPosition(space, goalPosition);
        return new Graph<>(nodes, edges, rootNode, goalNode);
    }

    /**
     * Shows which areas are blocked off
     * WARNING: Slows project considerably
     */
    private void displayBlockedAreas(Set<RouteNode> nodes, int widthDiff, int heightDiff) {
        nodes.stream()
                .filter(RouteNode::containsObstruction)
                .forEach(node -> searchGraphGraphics.add(
                        new RectangleGraphics(widthDiff, heightDiff, Color.BLUE, node.getTopLeftPosition())));
    }

    private void markArea(Toroidal2DPhysics space, double widthDiff, double heightDiff, Position obstructionPos, double buffer, boolean containsObstruction) {
        Position topLeft = new Position(obstructionPos.getX() - buffer, obstructionPos.getY() - buffer);
        Position bottomRight = new Position(obstructionPos.getX() + buffer, obstructionPos.getY() + buffer);

        for (double x = topLeft.getX(); x < bottomRight.getX(); x += widthDiff) {
            for (double y = topLeft.getY(); y < bottomRight.getY(); y += heightDiff) {
                Position contained = new Position(x, y);
                nodeForPosition(space, contained).setContainsObstruction(containsObstruction);
            }
        }
    }

    /**
     * Return a {@link RouteNode} object for a given position
     */
    private RouteNode nodeForPosition(Toroidal2DPhysics space, Position position) {
        return baseCandidates.get(gridXPosition(space, position.getX())).get(gridYPosition(space, position.getY()));
    }

    /**
     * Return which grid an x-position contains
     */
    private int gridXPosition(Toroidal2DPhysics space, double input) {
        return (int)MovementUtil.linearNormalize(0, space.getWidth(), 0, NUM_DIVISIONS_X, doubleRingMod(input, space.getWidth()));
    }

    /**
     * Return which grid an y-position contains
     */
    private int gridYPosition(Toroidal2DPhysics space, double input) {
        return (int)MovementUtil.linearNormalize(0, space.getHeight(), 0, NUM_DIVISIONS_Y, doubleRingMod(input, space.getHeight()));
    }

    /**
     * Allows us to do modulus with negative numbers
     * @param num number to mod
     * @param mod what number to divide by
     * @return a positive modded number
     */
    private int intRingMod(int num, int mod) {
        num %= mod;
        while (num < 0) {
            num += mod;
        }
        return num;
    }

    /**
     * Allows us to do modulus with negative (double) numbers
     * @param num number to mod
     * @param mod what number to divide by
     * @return a positive modded number
     */
    private double doubleRingMod(double num, double mod) {
        num %= mod;
        while (num < 0) {
            num += mod;
        }
        return num;
    }

    /**
     * Search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     */
    abstract List<Position> search(Toroidal2DPhysics space, Graph<RouteNode> searchGraph);

    /**
     * An estimate of the cost to get from the start to end position
     *
     * @param start The beginning position
     * @param end   The ending position
     * @return an estimate of the cost to get from start to end position, based on the {@link Route}'s implementation
     */
    abstract double heuristicCostEstimate(Toroidal2DPhysics space, Position start, Position end);

    /**
     * Work backwards, adding the nodes to the path
     *
     * @param current The node to work backwards from
     * @return The correctly ordered a* search path from start to current node
     */
    List<Position> reconstructPath(RouteNode current) {
        List<Position> path = new ArrayList<>();
        path.add(current.getCenter());
        RouteNode previous = current.getPrevious();
        while (previous != null) {
            path.add(previous.getCenter());
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
    public Set<SpacewarGraphics> getGraphics(Toroidal2DPhysics space) {
        Set<SpacewarGraphics> results = new HashSet<>();
        if (isDone()) {
            return results;
        }

        Position previous = steps.get(nextStep);
        Position goal = getGoal(space).getPosition();
        for (int i = nextStep + 1; i < steps.size(); ++i) {
            Position step = steps.get(i);
            if (step.equalsLocationOnly(goal)) {
                results.add(new TargetGraphics(8, step));
            } else if (step.equalsLocationOnly(getStep())) {
                results.add(new StarGraphics(4, Color.MAGENTA, step));
            } else {
                results.add(new StarGraphics(4, Color.WHITE, step));
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
    AbstractObject getGoal(Toroidal2DPhysics space) {
        if (goal instanceof MadeUpObject) {
            return goal;
        }
        return space.getObjectById(goal.getId());
    }

    public ShipRole getRole() {
        return role;
    }

    void updateIfObjectMoved(Toroidal2DPhysics space) {
        if (space.getCurrentTimestep() % 10 != 0  || steps == null || (steps.size() - 1 - nextStep) <= MIN_LOOK_BEHIND_TO_REPLAN) {
            return;
        }
        int lastPossibleNode = steps.size() - 1;

        Position lastStep = steps.get(lastPossibleNode);
        Position goalPosition = getGoal(space).getPosition();
        if (lastStep.equalsLocationOnly(goalPosition) || lastStep.equalsLocationOnly(nodeForPosition(space, goalPosition).getCenter())) {
            return;
        }

        int lookBehind = Math.min(LOOK_BEHIND_FROM_GOAL, lastPossibleNode - nextStep);
        Position starting = steps.get(lastPossibleNode - lookBehind);

        double distanceFromLookback = heuristicCostEstimate(space, starting, goalPosition);
        double distanceFromShip = heuristicCostEstimate(space, ship.getPosition(), goalPosition);
        // Recreate/search graph if looking back doesn't help
        if (distanceFromLookback >= distanceFromShip) {
            starting = ship.getPosition();
        }

        Graph<RouteNode> graph = createSearchGraph(space, ship, starting, goalPosition);
        List<Position> result = search(space, graph);

        if (result != null) {
            if (distanceFromLookback < distanceFromShip) {
                List<Position> newList = steps.subList(0, lastPossibleNode - lookBehind);
                newList.addAll(result);
                steps = newList;
            } else {
                steps = result;
            }
            steps.set(steps.size() - 1, getGoal(space).getPosition());
        }
    }
}