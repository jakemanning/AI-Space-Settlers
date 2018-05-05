package capp7507;

import spacesettlers.utilities.Position;

import java.util.Objects;

public class RouteNode {
    private final Position center;
    private Position topLeftPosition;
    private RouteNode cameFrom;
    private boolean containsObstruction;
    private double currentPathCost;
    private double distanceToGoal;
    private boolean explored;

    RouteNode(Position topLeftPosition, Position center) {
        this(topLeftPosition, Double.MAX_VALUE, Double.MAX_VALUE, center);
    }

    private RouteNode(Position topLeftPosition, double currentPathCost, double distanceToGoal, Position center) {
        this.topLeftPosition = topLeftPosition;
        this.currentPathCost = currentPathCost;
        this.distanceToGoal = distanceToGoal;
        this.center = center;
    }

    Position getTopLeftPosition() {
        return topLeftPosition;
    }

    public Position getCenter() {
        return center;
    }

    public double getCost() {
        return currentPathCost + distanceToGoal;
    }

    double getCurrentPathCost() {
        return currentPathCost;
    }

    void setCurrentPathCost(double currentPathCost) {
        this.currentPathCost = currentPathCost;
    }

    public double getDistanceToGoal() {
        return distanceToGoal;
    }

    void setDistanceToGoal(double distanceToGoal) {
        this.distanceToGoal = distanceToGoal;
    }

    boolean isExplored() {
        return explored;
    }

    void setExplored(boolean explored) {
        this.explored = explored;
    }

    RouteNode getPrevious() {
        return cameFrom;
    }

    void setPrevious(RouteNode cameFrom) {
        this.cameFrom = cameFrom;
    }

    boolean containsObstruction() {
        return containsObstruction;
    }

    void setContainsObstruction(boolean containsObstruction) {
        this.containsObstruction = containsObstruction;
    }

    void resetState() {
        this.cameFrom = null;
        this.containsObstruction = false;
        this.currentPathCost = Double.MAX_VALUE;
        this.distanceToGoal = Double.MAX_VALUE;
        this.explored = false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteNode)) return false;
        RouteNode node = (RouteNode) o;
        return topLeftPosition.equalsLocationOnly(node.topLeftPosition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topLeftPosition.getX(), topLeftPosition.getY());
    }
}