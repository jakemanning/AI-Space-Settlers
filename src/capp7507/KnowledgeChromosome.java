package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Arrays;
import java.util.Random;

/**
 * Essentially a wrapper around our coefficients that we use to avoid
 * - Generates coefficients
 * - Creates {@link AvoidAction}s out of a {@link KnowledgeState}
 * -
 */
public class KnowledgeChromosome {
    private double[] coefficients = null;

    KnowledgeChromosome() {
        Random rand = new Random();
        // randomly choose coefficients for the raw action
        if (coefficients == null) {
            coefficients = new double[5];
            for (int i = 0; i < coefficients.length; i++) {
                coefficients[i] = resetCoefficient(i, rand);
            }
        }
    }

    /**
     * Returns either the action currently specified by the policy or randomly selects one if this is a new state
     *
     * @param currentState
     * @return
     */
    public AvoidAction getCurrentAction(Toroidal2DPhysics space, Ship myShip, KnowledgeState currentState) {
        return rawAction(space, myShip, currentState);
    }

    /**
     * Generates a coefficient.
     * @param index
     * @param rand
     * @return
     */
    double resetCoefficient(int index, Random rand) {
        double coeff;
        if (index < 3) { // Angle calculations
            double angleVariance = Math.PI / 3;
            coeff = getGaussian(rand, angleVariance);
            coeff = limitRange(-2 * Math.PI, 2 * Math.PI, coeff);
        } else { // Distance calculations
            double distanceVariance = 20;
            coeff = getGaussian(rand, distanceVariance);
            coeff = rand.nextInt(20) + coeff;
            coeff = limitRange(-10, 40, coeff);
        }
        return coeff;
    }

    /**
     * We want most (68%) our coefficients to be between 2/3 * variance
     * @param rand random
     * @param variance what our variance should be
     * @return a coefficient
     */
    private double getGaussian(Random rand, double variance) {
        return rand.nextGaussian() * variance;
    }

    /**
     * Constraint these coefficients to some predefined range
     * @param min the min we want our coefficients to be
     * @param max the max we want our coefficients to be
     * @param val what value to constraint
     * @return the final coefficient
     */
    private double limitRange(double min, double max, double val) {
        if (val < min) {
            return min;
        } else if(val > max) {
            return max;
        } else {
            return val;
        }
    }

    /**
     * An action constructed using our coefficients, and {@link KnowledgeState}
     * @param space physics
     * @param ship which ship is avoiding
     * @param state our current state in space
     * @return an action to hopefully avoid our object
     */
    private AvoidAction rawAction(Toroidal2DPhysics space, Ship ship, KnowledgeState state) {
        double b = coefficients[0];
        double c = coefficients[1];
        double d = coefficients[2];
        double e = coefficients[3];
        double h = coefficients[4];

        double distanceToObstacle = state.getDistanceToObstacle();
        double angleToObstacle = state.getObstacleLocationAngle();
        double angleToObstacleMovement = state.getObstacleTrajectoryAngle();

        double angle = b * angleToObstacle + c * angleToObstacleMovement + d;
        double distance = e * distanceToObstacle + h;

        return AvoidAction.build(space, ship.getPosition(), angle, distance, state.getObstacle());
    }

    double[] getCoefficients() {
        return coefficients;
    }

    public KnowledgeChromosome deepCopy() {
        KnowledgeChromosome copy = new KnowledgeChromosome();
        if (coefficients != null) {
            copy.coefficients = Arrays.copyOf(coefficients, coefficients.length);
        }
        return copy;
    }
}
