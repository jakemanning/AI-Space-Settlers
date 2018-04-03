package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Arrays;
import java.util.Random;

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

    double resetCoefficient(int index, Random rand) {
        double coeff;
        if (index < 3) {
            double angleVariance = Math.PI / 3;
            coeff = getGaussian(rand, angleVariance);
            coeff = limitRange(-2 * Math.PI, 2 * Math.PI, coeff);
            System.out.println("angle coeff: " + coeff);
        } else {
            double distanceVariance = 20;
            coeff = getGaussian(rand, distanceVariance);
            coeff = rand.nextInt(20) + coeff;
            coeff = limitRange(-10, 40, coeff);
            System.out.println("distance coeff: " + coeff);
        }
        return coeff;
    }

    private double getGaussian(Random rand, double variance) {
        return rand.nextGaussian() * variance;
    }

    private double limitRange(double min, double max, double val) {
        if (val < min) {
            return min;
        } else if(val > max) {
            return max;
        } else {
            return val;
        }
    }

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
