package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

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
        int timeSinceLastSession = space.getCurrentTimestep() - lastSession.getTimestepStarted();
        if (timeSinceLastSession > 20) {
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
        sessions.stream()
                .filter(AvoidSession::isValid)
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
        sessions.peek().invalidate();
    }

    double averageFitness() {
        return sessions.stream()
                .filter(avoidSession -> !avoidSession.isValid())
                .filter(avoidSession -> !avoidSession.isSessionComplete())
                .mapToDouble(session -> session.result().evaluate())
                .average()
                .orElse(0);
    }
}
