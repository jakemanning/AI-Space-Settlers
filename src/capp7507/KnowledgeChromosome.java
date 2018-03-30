package capp7507;

import spacesettlers.actions.AbstractAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.HashMap;
import java.util.Random;

public class KnowledgeChromosome {
    private HashMap<KnowledgeState, AbstractAction> policy;

    public KnowledgeChromosome() {
        policy = new HashMap<>();
    }

    /**
     * Returns either the action currently specified by the policy or randomly selects one if this is a new state
     *
     * @param currentState
     * @return
     */
    public AbstractAction getCurrentAction(Toroidal2DPhysics space, Ship myShip, KnowledgeState currentState, Random rand) {
        if (!policy.containsKey(currentState)) {
            // randomly chose to either do nothing or go to the nearest
            // asteroid.  Note this needs to be changed in a real agent as it won't learn
            // much here!
            // TODO Check if this angle calc actually works
            double randomAngle = rand.nextDouble() * Math.PI * 2;
            double randomDistance = rand.nextDouble() * 10 * myShip.getRadius();
            AbstractAction action = AvoidAction.build(space, myShip.getPosition(), randomAngle, randomDistance);
            policy.put(currentState, action);
        }

        return policy.get(currentState);

    }
}
