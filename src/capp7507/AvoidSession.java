package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class AvoidSession {
    private double distanceAtAvoidBeginning;
    private double distanceAtAvoidEnd;
    private double energyAtAvoidBeginning;
    private double energyAtAvoidEnd;
    private boolean successfullyAvoided;
    private Instant timeStarted;
    private Instant timeCompleted;
    private UUID obstacleId;
    private UUID targetId;

    public AvoidSession(Toroidal2DPhysics space, Ship ship, AbstractObject target, AbstractObject obstacle) {
        this.successfullyAvoided = true;
        this.obstacleId = obstacle.getId();
        this.targetId = target.getId();
        timeStarted = Instant.now();
        energyAtAvoidBeginning = ship.getEnergy();
        distanceAtAvoidBeginning = space.findShortestDistance(ship.getPosition(), target.getPosition());
    }

    AvoidResult completeSession(Toroidal2DPhysics space, Ship ship) {
        AbstractObject target = target(space);
        if (target == null) {
            // TODO: Mayhap we ought to change this here, righto, god save the queen
            return null;
        }
        distanceAtAvoidEnd = space.findShortestDistance(ship.getPosition(), target.getPosition());
        energyAtAvoidEnd = ship.getEnergy();
        timeCompleted = Instant.now();

        return result();
    }

    public AvoidResult result() {
        double distanceChange = distanceAtAvoidBeginning - distanceAtAvoidEnd;
        double energySpent = energyAtAvoidBeginning - energyAtAvoidEnd;
        Duration timeSpent = Duration.between(timeStarted, timeCompleted);
        return new AvoidResult(successfullyAvoided, distanceChange, energySpent, timeSpent);
    }

    private AbstractObject target(Toroidal2DPhysics space) {
        return space.getObjectById(targetId);
    }

    private AbstractObject obstacle(Toroidal2DPhysics space) {
        return space.getObjectById(obstacleId);
    }

    boolean isSessionComplete() {
        return timeCompleted != null;
    }

    void setIncomplete() {
        this.timeCompleted = null;
    }

    Instant getTimeStarted() {
        return timeStarted;
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

    public class AvoidResult {
        private boolean successfullyAvoided;
        private double distanceChange;
        private double energySpent;
        private Duration timeSpent;

        private AvoidResult(boolean successfullyAvoided, double distanceChange, double energySpent, Duration timeSpent) {
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

        public Duration getTimeSpent() {
            return timeSpent;
        }

        public double getDistanceChange() {
            return distanceChange;
        }

        double evaluate() {
            // TODO: Make more smahter to increase knowledge
            if (successfullyAvoided) {
                return 1;
            } else {
                return 0;
            }
        }
    }
}
