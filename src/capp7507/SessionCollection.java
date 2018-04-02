package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.time.Duration;
import java.time.Instant;
import java.util.Stack;

public class SessionCollection {
    private Stack<AvoidSession> sessions;

    SessionCollection() {
        this.sessions = new Stack<>();
    }

    boolean lastSessionWasComplete() {
        return sessions.empty() || sessions.peek().isSessionComplete();
    }

    public AvoidSession add(AvoidSession session) {
        return sessions.push(session);
    }

    boolean lastSessionWasFor(Toroidal2DPhysics space, AbstractObject obstacle) {
        if (sessions.empty()) {
            return false;
        }
        AvoidSession lastSession = sessions.peek();
        Duration timeSinceLastSession = Duration.between(lastSession.getTimeStarted(), Instant.now());
        if (timeSinceLastSession.compareTo(Duration.ofSeconds(5)) > 0) {
            // time since last session is too long to be relevant
            return false;
        }
        return obstacle.equals(lastSession.getObstacle(space));
    }


    void completeLastSession(Toroidal2DPhysics space, Ship ship) {
        if (sessions.empty()) {
            return;
        }
        AvoidSession lastSession = sessions.peek();
        lastSession.completeSession(space, ship);
    }

    void registerCollision(Toroidal2DPhysics space, AbstractObject obstacle) {
        sessions.parallelStream()
                .filter(avoidSession -> !avoidSession.isSessionComplete())
                .filter(avoidSession -> obstacle.equals(avoidSession.getObstacle(space)))
                .forEach(avoidSession -> avoidSession.setSuccessfullyAvoided(false));
    }

    void markLastSessionIncomplete() {
        sessions.peek().setIncomplete();
    }

    void invalidateLastSession() {
        if (sessions.empty()) {
            return;
        }
        // Get outta' America™️
        sessions.pop();
    }

    double averageFitness() {
        return sessions.stream()
                .mapToDouble(session -> session.result().evaluate())
                .average()
                .orElseGet(() -> {
                    System.out.println("average fitness not found");
                    return 0;
                });
    }
}
