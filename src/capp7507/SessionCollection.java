package capp7507;

import java.util.Stack;

public class SessionCollection {
    private Stack<AvoidSession> sessions;

    SessionCollection() {
        this.sessions = new Stack<>();
    }

    boolean lastSessionWasComplete() {
        return sessions.isEmpty() || sessions.peek().isSessionComplete();
    }

    public AvoidSession add(AvoidSession session) {
        return sessions.push(session);
    }


    public void completeLastSession(double distanceAtAvoidEnd, boolean successfullyAvoided) {
        if(sessions.isEmpty()) {
            return;
        }
        AvoidSession lastSession = sessions.pop();
        lastSession.completeSession(distanceAtAvoidEnd, successfullyAvoided);
    }

    // TODO: Add 'sum' method that collects all the sessions and..does stuff?

}
