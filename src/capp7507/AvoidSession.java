package capp7507;

import java.time.Instant;

public class AvoidSession {
    private double distanceAtAvoidBeginning;
    private double distanceAtAvoidEnd;
    private boolean successfullyAvoided;
    private Instant timeCompleted;

    public AvoidSession(double distanceAtAvoidBeginning) {
        this.distanceAtAvoidBeginning = distanceAtAvoidBeginning;
    }

    public void completeSession(double distanceAtAvoidEnd, boolean successfullyAvoided) {
        this.distanceAtAvoidEnd = distanceAtAvoidEnd;
        this.successfullyAvoided = successfullyAvoided;
        this.timeCompleted = Instant.now();
    }

    public double getDistanceAtAvoidBeginning() {
        return distanceAtAvoidBeginning;
    }

    public double getDistanceAtAvoidEnd() {
        return distanceAtAvoidEnd;
    }

    public boolean isSuccessfullyAvoided() {
        return successfullyAvoided;
    }

    public Instant getTimeCompleted() {
        return timeCompleted;
    }

    boolean isSessionComplete() {
        return timeCompleted == null;
    }
}
