package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.UUID;

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
    private boolean isValid;
    private double distanceAtAvoidBeginning;
    private double distanceAtAvoidEnd;
    private double energyAtAvoidBeginning;
    private double energyAtAvoidEnd;
    private boolean successfullyAvoided;
    private int timestepStarted;
    private int timestepCompleted = Integer.MAX_VALUE;
    private UUID obstacleId;
    private UUID targetId;

    AvoidSession(Toroidal2DPhysics space, Ship ship, AbstractObject target, AbstractObject obstacle) {
        this.successfullyAvoided = true;
        this.isValid = true;
        this.obstacleId = obstacle.getId();
        this.targetId = target.getId();
        timestepStarted = space.getCurrentTimestep();
        energyAtAvoidBeginning = ship.getEnergy();
        distanceAtAvoidBeginning = space.findShortestDistance(ship.getPosition(), target.getPosition());
    }

    /**
     * Mark our last session complete
     * @param space physics
     * @param ship which ship to complete
     */
    void completeSession(Toroidal2DPhysics space, Ship ship) {
        AbstractObject target = target(space);
        if (target == null) {
            invalidate();
            return;
        }
        distanceAtAvoidEnd = space.findShortestDistance(ship.getPosition(), target.getPosition());
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
        return new AvoidResult(successfullyAvoided, distanceChange, energySpent, timeSpent);
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
        private boolean successfullyAvoided;
        private double distanceChange;
        private double energySpent;
        private int timeSpent;

        private AvoidResult(boolean successfullyAvoided, double distanceChange, double energySpent, int timeSpent) {
            this.successfullyAvoided = successfullyAvoided;
            this.distanceChange = distanceChange;
            this.timeSpent = timeSpent;
            this.energySpent = energySpent;
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

        /**
         * Fitness score calculation
         * @return the final fitness score
         */
        double evaluate() {
            double fitness = 0;
            if (!successfullyAvoided) {
                // penalize not avoiding the obstacle
                fitness -= 200;
            }
            // energy tends to be closeish to 10 times as much as distance or energy
            // we don't really care what the raw value of evaluate is,
            // but we do want to weigh these three values at a certain ratio
            // energy is about 3 times as important as distance and time
            fitness += distanceChange - energySpent / 3 - timeSpent;
            return fitness;
        }
    }
}
