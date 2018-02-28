package capp7507;

import spacesettlers.utilities.Position;

import java.util.Objects;

public class Node {
    private Node cameFrom;
    private Position position;
    private double currentPathCost;
    private double distanceToGoal;
    private boolean explored;

    Node(Position position) {
        this.position = position;
        this.currentPathCost = Double.MAX_VALUE;
        this.distanceToGoal = Double.MAX_VALUE;
    }

    Node(Position position, double currentPathCost, double distanceToGoal) {
        this.position = position;
        this.currentPathCost = currentPathCost;
        this.distanceToGoal = distanceToGoal;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public double getCost() {
        return currentPathCost + distanceToGoal;
    }

    public double getCurrentPathCost() {
        return currentPathCost;
    }

    public void setCurrentPathCost(double currentPathCost) {
        this.currentPathCost = currentPathCost;
    }

    public double getDistanceToGoal() {
        return distanceToGoal;
    }

    public void setDistanceToGoal(double distanceToGoal) {
        this.distanceToGoal = distanceToGoal;
    }

    public boolean isExplored() {
        return explored;
    }

    public void setExplored(boolean explored) {
        this.explored = explored;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Node)) return false;
        Node node = (Node) o;
        return position.equalsLocationOnly(node.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position.getX(), position.getY());
    }

    public Node getPrevious() {
        return cameFrom;
    }

    public void setPrevious(Node cameFrom) {
        this.cameFrom = cameFrom;
    }
}
