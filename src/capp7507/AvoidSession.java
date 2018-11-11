package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Movement;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.UUID;
import java.util.Vector;

/**
 * Used when training GA. An Avoid Session is created whenever a ship begins avoiding.
 * The same avoid session is used throughout our avoidance.
 * We mark the session as complete as soon as we finish avoiding (move towards an obstacle).
 * When we start avoiding again-to handle variability in our ship's target choosing-we keep track
 * of whether we completed a session within the last 20 timesteps. If the obstacle is the same, then we
 * reuse our previous avoid session.
 *
 * We mark AvoidSessions invalid if we hit any other object while avoiding
 * to handle weird cases like when we hit a beacon which changes our energy calculation
 *
 */
public class AvoidSession {
    private final Position originalPosition;
    private boolean isValid;
    private double distanceAtAvoidBeginning;
    private double distanceAtAvoidEnd;
    private double energyAtAvoidBeginning;
    private double energyAtAvoidEnd;
    private double difficulty;

    public boolean isSuccessfullyAvoided() {
        return successfullyAvoided;
    }

    private boolean successfullyAvoided;
    private int timestepStarted;
    private int timestepCompleted = Integer.MAX_VALUE;
    private UUID obstacleId;
    private UUID targetId;
    private final double MINIMUM_DIFFICULTY_POSSIBLE;

    AvoidSession(Toroidal2DPhysics space, Ship ship, AbstractObject target, AbstractObject obstacle) {
        this.successfullyAvoided = true;
        this.isValid = true;
        this.obstacleId = obstacle.getId();
        this.targetId = target.getId();
        this.originalPosition = obstacle.getPosition();
        timestepStarted = space.getCurrentTimestep();
        energyAtAvoidBeginning = ship.getEnergy();
        distanceAtAvoidBeginning = space.findShortestDistance(ship.getPosition(), target.getPosition());

        Vector2D goalVector = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
        Position intercept = MovementUtil.interceptPosition(space, obstacle.getPosition(), ship.getPosition());
        Vector2D collisionVector = space.findShortestDistanceVector(ship.getPosition(), intercept);
        double distanceFromObstacleToTarget = space.findShortestDistance(intercept, target.getPosition());
        double obstacleLocationAngle = goalVector.angleBetween(collisionVector);

        // Higher difficulty means lower penalty
        // Least hard: penalty = 3
        // Most hard: penalty = 0

        double distanceToObstaclePenalty = 1.0;
        double distanceFromGoalPenalty = 1.0;
        double obstacleAngleFromShipPenalty = 1.0;

        difficulty = MovementUtil.linearNormalize(0, MovementUtil.maxDistance(space), 0, distanceToObstaclePenalty, distanceFromObstacleToTarget); // The closer the obstacle is to the target, the harder it is to avoid
        difficulty += MovementUtil.linearNormalize(0, MovementUtil.maxDistance(space), 0, distanceFromGoalPenalty, space.findShortestDistance(ship.getPosition(), intercept)); // The closer we are to the obstacle, the harder it is
        difficulty += MovementUtil.linearNormalize(0, Math.PI / 2, 0, obstacleAngleFromShipPenalty, Math.abs(obstacleLocationAngle)); // The higher the angle, the easier it is

        MINIMUM_DIFFICULTY_POSSIBLE = distanceToObstaclePenalty + distanceFromGoalPenalty + obstacleAngleFromShipPenalty;
    }

    /**
     * Mark our last session complete
     * @param space physics
     * @param ship which ship to complete
     */
    void completeSession(Toroidal2DPhysics space, Ship ship) {
//        AbstractObject target = target(space);
//        if (target == null) {
//            invalidate();
//            return;
//        }
        distanceAtAvoidEnd = space.findShortestDistance(ship.getPosition(), originalPosition);
        energyAtAvoidEnd = ship.getEnergy();
        timestepCompleted = space.getCurrentTimestep();
    }

    /**
     * Our final result of our session, which we can use to evaluate the fitness
     * @return an AvoidResult
     */
    public AvoidResult result() {
        double distanceChange = distanceAtAvoidBeginning - distanceAtAvoidEnd;
        double energySpent = energyAtAvoidBeginning - energyAtAvoidEnd;
        int timeSpent = timestepCompleted - timestepStarted;
        return new AvoidResult(successfullyAvoided, distanceChange, distanceAtAvoidBeginning, energySpent, timeSpent, difficulty);
    }

    private AbstractObject target(Toroidal2DPhysics space) {
        return space.getObjectById(targetId);
    }

    boolean isSessionComplete() {
        return timestepCompleted != Integer.MAX_VALUE;
    }

    void setIncomplete() {
        this.timestepCompleted = Integer.MAX_VALUE;
    }

    int getTimestepStarted() {
        return timestepStarted;
    }

    AbstractObject getObstacle(Toroidal2DPhysics space) {
        return space.getObjectById(obstacleId);
    }

    void setSuccessfullyAvoided(boolean successfullyAvoided) {
        this.successfullyAvoided = successfullyAvoided;
    }

    public AbstractObject getTarget(Toroidal2DPhysics space) {
        return space.getObjectById(targetId);
    }

    public boolean isValid() {
        return isValid;
    }

    void invalidate() {
        isValid = false;
    }

    /**
     * The result of our AvoidSession, to calculate our fitness score
     */
    public class AvoidResult {
        private final double difficulty;
        private boolean successfullyAvoided;
        private double distanceChange;
        private double energySpent;
        private int timeSpent;
        private double initialDistance;

        private AvoidResult(boolean successfullyAvoided, double distanceChange, double initialDistance, double energySpent, int timeSpent, double difficulty) {
            this.successfullyAvoided = successfullyAvoided;
            this.distanceChange = distanceChange;
            this.initialDistance = initialDistance;
            this.timeSpent = timeSpent;
            this.energySpent = energySpent;
            this.difficulty = difficulty;
        }

        public boolean successfullyAvoided() {
            return successfullyAvoided;
        }

        public double getEnergySpent() {
            return energySpent;
        }

        public int getTimeSpent() {
            return timeSpent;
        }

        public double getDistanceChange() {
            return distanceChange;
        }

        double evaluate() {
            double fitness = 0;
            if (!successfullyAvoided) {
                fitness -= difficulty;
                System.out.printf("Failure! Fitness: %f\n", fitness);
            } else {
                // We should reward successfully avoiding by
                // Removing the difficulty from the lowest difficulty (highest possible number: 3.0)
                // And adding 1.0 as a bonus for successfully avoiding
                double BONUS_FOR_SUCCESS = 1.0;
                fitness += (MINIMUM_DIFFICULTY_POSSIBLE - difficulty) + BONUS_FOR_SUCCESS;
                System.out.printf("Succcess! Fitness: %f\n", fitness);
            }

            return fitness;
        }
    }
}
