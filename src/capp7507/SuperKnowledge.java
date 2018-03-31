package capp7507;

import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.UUID;

class SuperKnowledge {

    /**
     * The current policy for the team
     */
    private KnowledgeChromosome currentPolicy;

    /**
     * The current population (either being built or being evaluated)
     */
    private KnowledgePopulation population;

    private int steps = 0;
    private final int EVALUATION_STEPS = 2000;
    private final int POPULATION_SIZE = 25;
    private Map<UUID, SessionCollection> sessions;

    SuperKnowledge() {
        sessions = new HashMap<>();
    }

//    void updateSession(UUID uuid, AvoidSession avoidSession) {
//        // TODO is this, in fact, correct?
//        SessionCollection currentSessions = getSessionsFor(uuid);
//        if(currentSessions.lastSessionWasComplete()) {
//            currentSessions.add(avoidSession);
//        } else {
//            currentSessions.completeLastSession();
//            currentSessions.push(avoidSession);
//        }
//    }

    SessionCollection getSessionsFor(UUID uuid) {
        if(!sessions.containsKey(uuid)) {
            // We gettin' litty up in here boiz, so let's start learnin'
            sessions.put(uuid, new SessionCollection());
        }
        return sessions.get(uuid);
    }

    /**
     *
     * @param space
     */
    void think(Toroidal2DPhysics space) {
        // increment the step counter
        steps++;

        // if the step counter is modulo EVALUATION_STEPS, then evaluate this member and move to the next one
        if (steps % EVALUATION_STEPS == 0) {
            // note that this method currently scores every policy as zero as this is part of
            // what the student has to do
            population.evaluateFitnessForCurrentMember(space);

            // move to the next member of the population
            currentPolicy = population.getNextMember();

            if (population.isGenerationFinished()) {
                // note that this is also an empty method that a student needs to fill in
                population.makeNextGeneration();

                currentPolicy = population.getNextMember();
            }

        }
    }
}
