package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Stack;

/**
 * Stores a collection of avoid sessions
 */
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

    /**
     * Returns whether the last avoid session was to avoid the given obstacle.
     * Also considers how long ago the last session was in case it
     * has been a while and these sessions really should be considered separate.
     *
     * @param space    physics
     * @param obstacle Candidate obstacle
     * @return True if the last session is recent and for the last obstacle
     */
    boolean lastSessionWasFor(Toroidal2DPhysics space, AbstractObject obstacle) {
        if (sessions.empty()) {
            return false;
        }
        AvoidSession lastSession = sessions.peek();
        int timeStepsSinceLastSession = space.getCurrentTimestep() - lastSession.getTimestepStarted();
        if (timeStepsSinceLastSession > 20) {
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

    /**
     * Mark the avoid session avoiding the given obstacle as unsuccessful.
     * Should be called when the ship collides with the obstacle.
     *
     * @param space    physics
     * @param obstacle AbstractObject we were trying to avoid
     */
    void markAvoidanceAsUnsuccessful(Toroidal2DPhysics space, AbstractObject obstacle) {
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
        sessions.peek().invalidate();
    }

    /**
     * Average fitness of all the avoid sessions in the collection
     *
     * @return double representing the average
     */
    double averageFitness() {
        return sessions.stream()
                .filter(AvoidSession::isValid)
                .filter(AvoidSession::isSessionComplete)
                .mapToDouble(session -> session.result().evaluate())
                .average()
                .orElse(0);
    }
}
