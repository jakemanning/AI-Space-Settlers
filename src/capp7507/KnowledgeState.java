package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.io.Serializable;
import java.util.Objects;

public class KnowledgeState implements Serializable {
    private double distanceToObstacle;
    // Angle difference between the line from the ship to the target and the line from the ship to the obstacle
    // This tells us whether the obstacle is to the ship's left or right
    private double obstacleLocationAngle;
    // Angle difference between the line from the ship to the target and the line from the obstacle indicating its velocity
    // This tells us whether the obstacle is moving towards the ship's left or right
    private double obstacleTrajectoryAngle;
    private AbstractObject obstacle;
    private AbstractObject target;

    public KnowledgeState(double distanceToObstacle, double obstacleLocationAngle, double obstacleTrajectoryAngle,
                          AbstractObject obstacle, AbstractObject target) {
        this.distanceToObstacle = distanceToObstacle;
        this.obstacleLocationAngle = obstacleLocationAngle;
        this.obstacleTrajectoryAngle = obstacleTrajectoryAngle;
        this.obstacle = obstacle;
        this.target = target;
    }

    public static KnowledgeState build(Toroidal2DPhysics space, Ship myShip, AbstractObject obstacle, AbstractObject target) {
        Position shipPosition = myShip.getPosition();
        Position obstaclePosition = obstacle.getPosition();
        double distanceToObstacle = space.findShortestDistance(shipPosition, obstaclePosition);

        Vector2D goalVector = space.findShortestDistanceVector(shipPosition, target.getPosition());
        Vector2D collisionVector = space.findShortestDistanceVector(shipPosition, obstaclePosition);
        double obstacleLocationAngle = goalVector.angleBetween(collisionVector);

        Vector2D obstacleVelocity = obstaclePosition.getTranslationalVelocity();
        double obstacleTrajectoryAngle = goalVector.angleBetween(obstacleVelocity);

        return new KnowledgeState(distanceToObstacle, obstacleLocationAngle, obstacleTrajectoryAngle, obstacle, target);
    }

    double getDistanceToObstacle() {
        return distanceToObstacle;
    }

    double getObstacleLocationAngle() {
        return obstacleLocationAngle;
    }

    double getObstacleTrajectoryAngle() {
        return obstacleTrajectoryAngle;
    }

    AbstractObject getObstacle() {
        return obstacle;
    }

    public AbstractObject getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeState)) return false;
        KnowledgeState that = (KnowledgeState) o;
        return Double.compare(that.distanceToObstacle, distanceToObstacle) == 0 &&
                Double.compare(that.obstacleLocationAngle, obstacleLocationAngle) == 0 &&
                Double.compare(that.obstacleTrajectoryAngle, obstacleTrajectoryAngle) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(distanceToObstacle, obstacleLocationAngle, obstacleTrajectoryAngle);
    }
}
