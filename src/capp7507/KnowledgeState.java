package capp7507;

import spacesettlers.objects.AbstractObject;

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
