package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Logger;


/**
 * A collection of {@link AvoidSession}s
 * Simply makes it easy to deal with a lot of sessions
 */
public class SessionCollection {
    private Logger logger = Logger.getLogger(SessionCollection.class.getName());
    private Map<UUID, Stack<AvoidSession>> sessionMap;
    private final int RELEVANT_THRESHOLD = 5;

    SessionCollection() {
        logger.addHandler(new ConsoleHandler());
        sessionMap = new HashMap<>();
    }

    private Stack<AvoidSession> getSessionsFor(UUID obstacleUUID) {
        if (!sessionMap.containsKey(obstacleUUID)) {
            sessionMap.put(obstacleUUID, new Stack<>());
        }
        return sessionMap.get(obstacleUUID);
    }

    boolean manageSessionFor(UUID obstacleUUID) {
        return true;
//        return sessions.empty() || sessions.peek().isComplete();
    }

    public AvoidSession add(AvoidSession session) {
        UUID obstacleUUID = session.getObstacleId();
        Stack<AvoidSession> sessionsForObstacle = getSessionsFor(obstacleUUID);
        return sessionsForObstacle.push(session);
    }

    /**
     * Returns whether the last avoid session was to avoid the given obstacle.
     * Also considers how long ago the last session was in case it
     * has been a while and these sessions really should be considered separate.
     *
     * @param obstacleUUID Candidate obstacle
     * @return True if we should create a new session (FOR NOW)
     */
    boolean shouldCreateNewSession(Toroidal2DPhysics space, Ship ship, UUID obstacleUUID, double score) {
        Stack<AvoidSession> sessionsForObstacle = getSessionsFor(obstacleUUID);
        if (sessionsForObstacle.empty()) {
            // We're avoiding a new obstacle. Complete all the previous sessions
            completeAllSessions(space, ship, score);
            logger.fine("Session is empty");
            return true;
        }

        // TODO: (Maybe? Not sure) instead of jankily stopping/starting the session, make it so it doesn't matter whether there's time between an avoid session?
        AvoidSession latestSession = sessionsForObstacle.peek();

        if (latestSession.isComplete()) {
            // We were previously avoiding this obstacle. Let's check if we should 'resume' that session.
            int timeStepsSinceLastSession = space.getCurrentTimestep() - latestSession.getTimestepCompleted();
            if (lastSessionIsRelevant(timeStepsSinceLastSession)) {
                // We should resume that session bc it hasn't been very long since we stopped that session
                latestSession.setIncomplete();
                return false;
            } else {
                // It's been a while since we avoided this obstacle. eventually pick up where we left off.
                logger.fine(String.format("Session for %s has expired", obstacleUUID));
                return true;
            }
        } else {
            // We are still avoiding this obstacle. Wat do?
            return false;
        }
    }

    private boolean lastSessionIsRelevant(int timeStepsSinceLastSession) {
        return timeStepsSinceLastSession < RELEVANT_THRESHOLD;
    }

    void completeAllSessions(Toroidal2DPhysics space, Ship ship, double score) {
        sessionMap.values().stream()
                .filter(collection -> !collection.empty())
                .map(Stack::peek)
                .filter(session -> !session.isComplete())
                .forEach(session -> {
                    logger.fine(String.format("Completing session for %s", session.getObstacleId().toString()));
                    session.completeSession(space, ship, score);
                });
    }

    /**
     * Mark the avoid session avoiding the given obstacle as unsuccessful.
     * Should be called when the ship collides with the obstacle.
     *
     * @param obstacleUUID UUID of obstacle we were trying to avoid
     */
    void markAvoidanceAsUnsuccessfulFor(UUID obstacleUUID) {
        Stack<AvoidSession> sessionsForObstacle = getSessionsFor(obstacleUUID);
        if (sessionsForObstacle.empty()) {
            return;
        }

        AvoidSession latestSession = sessionsForObstacle.peek();
        if (latestSession.isValid() && !latestSession.isComplete()) {
            logger.fine(String.format("Marking unsuccessful for %s", obstacleUUID.toString()));
            latestSession.setSuccessfullyAvoided(false);
        }
    }

    void markLastSessionIncompleteFor(UUID obstacleUUID) {
        // FIXME: Here, I should somehow update the totals for the energy/etc.
        Stack<AvoidSession> sessionsForObstacle = getSessionsFor(obstacleUUID);
        if (sessionsForObstacle.empty()) {
            return;
        }
        AvoidSession latestSession = sessionsForObstacle.peek();
        latestSession.setIncomplete();
//        logger.fine(String.format("Last session was incomplete f %s", obstacleUUID.toString()));
    }

    void invalidateLastSessionFor(UUID obstacleUUID, String reason) {
        Stack<AvoidSession> sessionsForObstacle = getSessionsFor(obstacleUUID);
        if (sessionsForObstacle.empty()) {
            return;
        }
        AvoidSession latestSession = sessionsForObstacle.peek();
        latestSession.invalidate();
        logger.fine(String.format("Invalidating last session for %s - %s", obstacleUUID.toString(), reason));
    }

    /**
     * Average fitness of all the avoid sessions in the collection
     *
     * @return double representing the average
     */
    double averageFitness() {
        return sessionMap.values().stream().flatMap(Collection::stream)
                .filter(AvoidSession::isValid)
                .filter(AvoidSession::isComplete)
                .filter(AvoidSession::sessionWasLongEnough)
                .mapToDouble(session -> session.result().evaluate())
                .sum();
    }
}
