package capp7507;

import spacesettlers.actions.AbstractAction;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.HashMap;
import java.util.Random;

public class KnowledgeChromosome {
    private HashMap<KnowledgeState, AbstractAction> policy;
    private double[] coefficients = null;

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
            // randomly choose coeffients for the raw action
            if (coefficients == null) {
                double a = rand.nextDouble() * 100;
                double b = rand.nextDouble() * 100;
                double c = rand.nextDouble() * 100;
                double d = rand.nextDouble() * 100;
                double e = rand.nextDouble() * 100;
                double f = rand.nextDouble() * 100;
                double g = rand.nextDouble() * 100;
                double h = rand.nextDouble() * 100;

                coefficients = new double[]{a, b, c, d, e, f, g, h};
            }

            AvoidAction action = rawAction(space, myShip, currentState);

            policy.put(currentState, action);
        }

        return policy.get(currentState);

    }

    private AvoidAction rawAction(Toroidal2DPhysics space, Ship ship, KnowledgeState state) {
        double a = coefficients[0];
        double b = coefficients[1];
        double c = coefficients[2];
        double d = coefficients[3];
        double e = coefficients[4];
        double f = coefficients[5];
        double g = coefficients[6];
        double h = coefficients[7];

        double distanceToObstacle = state.getDistanceToObstacle();
        double angleToObstacle = state.getObstacleLocationAngle();
        double angleToObstacleMovement = state.getObstacleTrajectoryAngle();

        double angle = a * distanceToObstacle + b * angleToObstacle + c * angleToObstacleMovement + d;
        double distance = e * distanceToObstacle + f * angleToObstacle + g * angleToObstacleMovement + h;

        return AvoidAction.build(space, ship.getPosition(), angle, distance, state.getObstacle());
    }
}
