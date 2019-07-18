package capp7507;

import spacesettlers.graphics.LineGraphics;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.io.Serializable;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Used to build our avoid actions. Given a ship's current 'state',
 * we want to build an {@link AvoidAction}
 */
public class KnowledgeState implements Serializable {
    static final int TRAJECTORY_NUMBER_OF_DIVISIONS = 8;
    static final int ANGLE_NUMBER_OF_DIVISIONS = 16;
    private int trajectoryCategory;
    private int angleCategory;
    private double distanceToObstacle;
    // Angle difference between the line from the ship to the target and the line from the ship to the obstacle
    // This tells us whether the obstacle is to the ship's left or right
    private double obstacleLocationAngle;
    // Angle difference between the line from the ship to the target and the line from the obstacle indicating its velocity
    // This tells us whether the obstacle is moving towards the ship's left or right
    private double obstacleTrajectoryAngle;
    private AbstractObject obstacle;
    private AbstractObject target;
    private Logger logger = Logger.getLogger(KnowledgeState.class.getName());

    public KnowledgeState(double distanceToObstacle, double obstacleLocationAngle, double obstacleTrajectoryAngle,
                          AbstractObject obstacle, AbstractObject target) {

        this.distanceToObstacle = distanceToObstacle;
        this.obstacleLocationAngle = obstacleLocationAngle;
        this.obstacleTrajectoryAngle = obstacleTrajectoryAngle;
        this.obstacle = obstacle;
        this.target = target;
        this.angleCategory = getAngleCategory(obstacleLocationAngle);
        this.trajectoryCategory = -1;// getTrajectoryCategory(obstacleTrajectoryAngle);

    }

    /**
     * Uses distance and angle calculation to build our state whenever we need to avoid
     * @param space physics
     * @param myShip current ship to build knowledge
     * @param obstacle which obstacle we're avoiding
     * @param target where we're heading
     * @return our built state
     */
    public static KnowledgeState build(Toroidal2DPhysics space, Ship myShip, AbstractObject obstacle, AbstractObject target, GraphicsUtil util) {
        // TODO: The angle category doesn't seem to be updating when I want it to. Maybe instead I should be using the angle from the orientation of the ship to the obstacle
        Position shipPosition = myShip.getPosition();
        Position obstaclePosition = obstacle.getPosition();
        Position obstacleInterceptPosition = MovementUtil.interceptPosition(space, obstaclePosition, shipPosition); // (Currently obstacles shouldn't be moving
        Position targetInterceptPosition = MovementUtil.interceptPosition(space, target.getPosition(), shipPosition); // Currently targets shouldn't be moving
        double distanceToObstacle = space.findShortestDistance(shipPosition, obstacleInterceptPosition);
        double shipOrientation = myShip.getPosition().getOrientation();
        final int length = 25;
        Vector2D orientationVector = Vector2D.fromAngle(shipOrientation, length);
        Vector2D collisionVector = space.findShortestDistanceVector(shipPosition, obstacleInterceptPosition);
        double obstacleLocationAngle = orientationVector.angleBetween(collisionVector);

        Vector2D obstacleVelocity = obstaclePosition.getTranslationalVelocity();
        Position added = new Position(new Vector2D(shipPosition).add(obstacleVelocity));
        double obstacleTrajectoryAngle = 0; //goalVector.angleBetween(obstacleVelocity);

        ////// Graphics Stuff here
        util.addGraphic(new LineGraphics(shipPosition, shipPosition, orientationVector));

        util.addLineGraphic(space, shipPosition, obstacleInterceptPosition);

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

    int getTrajectoryCategory() {
        return trajectoryCategory;
    }

    int getAngleCategory() {
        return angleCategory;
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

    private int getTrajectoryCategory(double obstacleTrajectoryAngle) {
        if(obstacleTrajectoryAngle < 0) {
            obstacleTrajectoryAngle = (2 * Math.PI) - (-1 * obstacleTrajectoryAngle);
        }
        double normalized = MovementUtil.linearNormalize(0, 2 * Math.PI, 0, TRAJECTORY_NUMBER_OF_DIVISIONS - 1, obstacleTrajectoryAngle);
        return (int) Math.floor(normalized);
    }

    private int getAngleCategory(double obstacleLocationAngle) {
        double normalized = MovementUtil.linearNormalize(-Math.PI, Math.PI, 0, ANGLE_NUMBER_OF_DIVISIONS, obstacleLocationAngle);
        int normalizedInt = (int) Math.floor(normalized);
        return normalizedInt % ANGLE_NUMBER_OF_DIVISIONS;
    }
}
