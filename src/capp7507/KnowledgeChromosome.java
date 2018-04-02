package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Random;

public class KnowledgeChromosome {
    private double[] coefficients = null;

    public KnowledgeChromosome() {
    }

    /**
     * Returns either the action currently specified by the policy or randomly selects one if this is a new state
     *
     * @param currentState
     * @return
     */
    public AvoidAction getCurrentAction(Toroidal2DPhysics space, Ship myShip, KnowledgeState currentState, Random rand) {
        // randomly choose coeffients for the raw action
        if (coefficients == null) {
            coefficients = new double[8];
            for (int i = 0; i < coefficients.length; i++) {
                coefficients[i] = resetCoefficient(i, rand);
            }
        }

        return rawAction(space, myShip, currentState);
    }

    public double resetCoefficient(int index, Random rand) {
        if (index < 4) {
            return (rand.nextDouble() * 2 * Math.PI) - Math.PI;
        } else {
            return (rand.nextDouble() * 100) - 50;
        }
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

    public double[] getCoefficients() {
        return coefficients;
    }
}
