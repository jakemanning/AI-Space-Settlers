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
    private static int NUM_DIVISIONS_X = 100; // Divisible by 1600
    private static int NUM_DIVISIONS_Y = 60; // Divisible by 1080
    private Set<SpacewarGraphics> searchGraphGraphics = new HashSet<>();
    List<Position> steps;
    private AbstractObject goal;
    Toroidal2DPhysics space;
    private static List<List<RouteNode>> baseCandidates;

    Route(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        this.goal = goal;
        this.space = space;
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

    // Determine which step a path is blocked
    // Only looks at the next 5 nodes
    // (was used for splicing, couldn't get to work yet)
    int pathBlockedAtStep(Toroidal2DPhysics space, Ship ship, Set<AbstractObject> obstructions) {
        for (int i = nextStep + 1, count = 0; i < steps.size() && count < 5; ++i, ++count) {
            Position currentStep = steps.get(i - 1);
            Position nextStep = steps.get(i);

            if (!space.isPathClearOfObstructions(currentStep, nextStep, obstructions, ship.getRadius())) {
                return i;
            }
        }
        return -1;
    }

    // Experimental
//    void spliceAt(int index) {
//        if (index >= steps.size()) {
//            return;
//        }
//        Graph<RouteNode> spliceGraph = createSearchGraph(ship, ship.getPosition(), steps.get(index));
//        List<Position> splicePositions = search(spliceGraph);
//        splicePositions.addAll(steps.subList(index, steps.size()));
//        steps = new ArrayList<>(splicePositions);
//    }

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
    Graph<RouteNode> createSearchGraph(Ship ship, Position initialShipPosition, Position goalPosition) {
        Set<RouteNode> nodes = new HashSet<>();
        Map<RouteNode, Set<RouteNode>> edges = new HashMap<>();

        Set<AbstractObject> obstructions = SpaceSearchUtil.getObstructions(space, ship, goal);

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

        RouteNode rootNode = nodeForPosition(initialShipPosition);
        rootNode.setCurrentPathCost(0);
        rootNode.setDistanceToGoal(heuristicCostEstimate(initialShipPosition, goalPosition));

        // Mark nodes as invalid if contains an obstruction
        // TODO: This doesn't work very well. So fix it dear 'liza
        for (AbstractObject obstruction : obstructions) {
            Position obstructionPos = obstruction.getPosition();
            Position topLeft = new Position(obstructionPos.getX() - obstruction.getRadius() - ship.getRadius(), obstructionPos.getY() - obstruction.getRadius() - ship.getRadius());
            Position bottomRight = new Position(obstructionPos.getX() + obstruction.getRadius() + ship.getRadius(), obstructionPos.getY() + obstruction.getRadius() + ship.getRadius());

            for (double x = topLeft.getX(); x < bottomRight.getX(); x += widthDiff) {
                for (double y = topLeft.getY(); y < bottomRight.getY(); y += heightDiff) {
                    Position contained = new Position(x, y);
                    nodeForPosition(contained).setContainsObstruction(true);
                }
            }
            nodeForPosition(obstructionPos).setContainsObstruction(true);
        }

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

        nodes.stream()
                .filter(RouteNode::containsObstruction)
                .forEach(node -> searchGraphGraphics.add(new RectangleGraphics((int)widthDiff, (int)heightDiff, Color.BLUE, node.getTopLeftPosition())));

        RouteNode goalNode = nodeForPosition(goalPosition);
        return new Graph<>(nodes, edges, rootNode, goalNode);
    }

    private RouteNode nodeForPosition(Position position) {
        return baseCandidates.get(gridXPosition(position.getX())).get(gridYPosition(position.getY()));
    }

    private int gridXPosition(double input) {
        return (int)MovementUtil.linearNormalize(0, space.getWidth(), 0, NUM_DIVISIONS_X, doubleRingMod(input, space.getWidth()));
    }

    private int gridYPosition(double input) {
        return (int)MovementUtil.linearNormalize(0, space.getHeight(), 0, NUM_DIVISIONS_Y, doubleRingMod(input, space.getHeight()));
    }

    private int intRingMod(int num, int mod) {
        num %= mod;
        if (num < 0) {
            num += mod;
        }
        return num;
    }

    private double doubleRingMod(double num, double mod) {
        num %= mod;
        if (num < 0) {
            num += mod;
        }
        return num;
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
    public AbstractObject getGoal() {
        return goal;
    }
}