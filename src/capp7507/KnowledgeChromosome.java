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
            coefficients = new double[KnowledgeState.ANGLE_NUMBER_OF_DIVISIONS + KnowledgeState.TRAJECTORY_NUMBER_OF_DIVISIONS];
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
        double coeff = 0;
        if (index < KnowledgeState.ANGLE_NUMBER_OF_DIVISIONS) { // Angle to obstacle
            coeff = getGaussian(rand, 0.0, 1);
            coeff = limitRange(-2, 2, coeff);
        } else { // Trajectory calculations
            coeff = getGaussian(rand, 0.0 , 0.3);
            coeff = limitRange(-0.75, 0.75, coeff);
        }
        return coeff;
    }

    /**
     * We want most (68%) our coefficients to be between 2/3 * variance
     * @param rand random
     * @param variance what our variance should be
     * @return a coefficient
     */
    private double getGaussian(Random rand, double mean, double variance) {
        return mean + rand.nextGaussian() * variance;
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
        double a = coefficients[state.getAngleCategory()]; // Angle to obstacle
        double b = coefficients[state.getTrajectoryCategory() + KnowledgeState.ANGLE_NUMBER_OF_DIVISIONS]; // offset to account for first coefficients (angle to obstacle)

//        double distanceToObstacle = state.getDistanceToObstacle();
        double angleToObstacle = state.getObstacleLocationAngle();
        double angleToObstacleMovement = state.getObstacleTrajectoryAngle();

        double angle = a * angleToObstacle + b * angleToObstacleMovement;

        return AvoidAction.build(space, ship.getPosition(), angle, 1, state.getObstacle());
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
