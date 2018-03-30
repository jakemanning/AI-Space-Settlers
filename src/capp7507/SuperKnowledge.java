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
    private int evaluationSteps = 2000;
    private int populationSize = 25;
    private Map<UUID, Stack<AvoidSession>> sessions;

    SuperKnowledge() {
        sessions = new HashMap<>();
    }

    void addSession(UUID uuid, AvoidSession avoidSession) {
        // TODO is this, in fact, correct?
        if(!sessions.containsKey(uuid)) {
            sessions.put(uuid, new Stack<>());
        }
        Stack<AvoidSession> currentSessions = sessions.get(uuid);
        if(currentSessions.isEmpty() || currentSessions.peek().isSessionComplete()) {
            currentSessions.push(avoidSession);
        } else if(!currentSessions.peek().isSessionComplete()) {
            AvoidSession session = currentSessions.pop();
            currentSessions.push(avoidSession);
        }
    }

    /**
     *
     * @param space
     */
    void think(Toroidal2DPhysics space) {
        // increment the step counter
        steps++;

        // if the step counter is modulo evaluationSteps, then evaluate this member and move to the next one
        if (steps % evaluationSteps == 0) {
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
