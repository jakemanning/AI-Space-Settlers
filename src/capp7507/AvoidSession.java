package capp7507;

import java.time.Duration;
import java.time.Instant;

public class AvoidSession {
    private double distanceAtAvoidBeginning;
    private double distanceAtAvoidEnd;
    private boolean successfullyAvoided;
    private Instant timeCompleted;

    // TODO: Store energy used also

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

    boolean isSessionCompleteWithin(Instant now) {
//        return isSessionComplete() || Duration.between(timeCompleted, now).getSeconds() < 0.1;
        return false;
    }
}
