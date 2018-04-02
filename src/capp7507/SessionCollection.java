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

    public boolean lastSessionWasFor(Toroidal2DPhysics space, AbstractObject obstacle) {
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


    public void completeLastSession(Toroidal2DPhysics space, Ship ship) {
        if(sessions.isEmpty()) {
            return;
        }
        AvoidSession lastSession = sessions.pop();
        lastSession.completeSession(space, ship);
    }

    public void registerCollision(Toroidal2DPhysics space, AbstractObject obstacle) {
        sessions.parallelStream()
                .filter(avoidSession -> !avoidSession.isSessionComplete())
                .filter(avoidSession -> obstacle.equals(avoidSession.getObstacle(space)))
                .forEach(avoidSession -> avoidSession.setSuccessfullyAvoided(false));
    }

    public void markLastSessionIncomplete() {
        sessions.peek().setIncomplete();
    }

    public double averageFitness() {
        return sessions.stream()
                .mapToDouble(session -> session.result().evaluate())
                .average()
                .orElseGet(() -> {
                    System.out.println("average fitness not found");
                    return 0;
                });
    }

    // TODO: Add 'sum' method that collects all the sessions and..does stuff?

}
