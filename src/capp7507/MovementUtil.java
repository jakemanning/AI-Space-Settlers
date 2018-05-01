package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.Collection;

import static capp7507.JakeTeamClient.TARGET_SHIP_SPEED;

/**
 * A collection of helpful utilities concerning movement
 */
class MovementUtil {
    /*
     * Returns the inverse of linear normalization. Instead of scaling from newMin to newMax,
     * our scale ends up being from newMin to newMax.
     *
     * For example first range is (0.1 to 0.6), and second range is (0.7 to 1.2).
     * Input of 0.2 will return 1.1, the ratio of the input between the first range, normalized to the second range (inverse)
     *
     * @param oldMin Original Linear scale start
     * @param oldMax Original scale end
     * @param newMin New linear scale start
     * @param newMax New linear scale end
     * @param input  What we want to convert
     * @return Linearly scaled integer from old range to new range
     */
    static double linearNormalizeInverse(double oldMin, double oldMax, double newMin, double newMax, double input) {
        return newMax - linearNormalize(oldMin, oldMax, newMin, newMax, input) + newMin;
    }

    /**
     * Converts from a linear scale from x1 to x2 to linear scale from y1 to y2
     * For example, if the first linear scale is from 0 to 1, and the linear scale is 1 to 90,
     * then an input will be converted from the first linear scale to the second linear scale (adhering to the original ratio)
     * <p>
     * For example first range is (0.1 to 0.6), and second range is (0.7 to 1.2).
     * Input of 0.3 will return 0.9, the ratio of the input between the first range, normalized to the second range
     *
     * @param oldMin Original Linear scale start
     * @param oldMax Original scale end
     * @param newMin New linear scale start
     * @param newMax New linear scale end
     * @param input  What we want to convert
     * @return Linearly scaled integer from old range to new range
     */
    static double linearNormalize(double oldMin, double oldMax, double newMin, double newMax, double input) {
        if (input < oldMin) {
            input = oldMin;
        } else if (input > oldMax) {
            input = oldMax;
        }

        double oldRange = oldMax - oldMin;
        if (oldRange == 0) {
            return newMin;
        } else {
            double newRange = newMax - newMin;
            return (((input - oldMin) * newRange) / oldRange) + newMin;
        }
    }

    /**
     * Figure out where the moving target and the ship will meet if the target maintains its current velocity
     * and the ship moves roughly at TARGET_SHIP_SPEED
     * https://stackoverflow.com/questions/2248876/2d-game-fire-at-a-moving-target-by-predicting-intersection-of-projectile-and-u
     *
     * @param space          The Toroidal2DPhysics for the game
     * @param targetPosition Position of the target at this instant
     * @param shipLocation   Position of the ship at this instant
     * @return Position to aim the ship in order to collide with the target
     */
    static Position interceptPosition(Toroidal2DPhysics space, Position targetPosition, Position shipLocation) {
        // component velocities of the target
        double targetVelX = targetPosition.getTranslationalVelocityX();
        double targetVelY = targetPosition.getTranslationalVelocityY();
        // component location of the target
        double targetX = targetPosition.getX();
        double targetY = targetPosition.getY();
        // component location of the ship
        double shipX = shipLocation.getX();
        double shipY = shipLocation.getY();

        // handle wrap around paths in Toroidal2DPhysics
        double negativeTargetX = targetX - space.getWidth();
        if (Math.abs(negativeTargetX - shipX) < Math.abs(targetX - shipX)) {
            targetX = negativeTargetX;
        }
        double extraTargetX = targetX + space.getWidth();
        if (Math.abs(extraTargetX - shipX) < Math.abs(targetX - shipX)) {
            targetX = extraTargetX;
        }
        double negativeTargetY = targetY - space.getHeight();
        if (Math.abs(negativeTargetY - shipY) < Math.abs(targetY - shipY)) {
            targetY = negativeTargetY;
        }
        double extraTargetY = targetY + space.getHeight();
        if (Math.abs(extraTargetY - shipY) < Math.abs(targetY - shipY)) {
            targetY = extraTargetY;
        }

        // Math to compute the intercept
        double shipSpeed = Math.max(TARGET_SHIP_SPEED, shipLocation.getTotalTranslationalVelocity());
        double a = Math.pow(targetVelX, 2) + Math.pow(targetVelY, 2) - Math.pow(shipSpeed, 2);
        double b = 2 * (targetVelX * (targetX - shipX) + targetVelY * (targetY - shipY));
        double c = Math.pow(targetX - shipX, 2) + Math.pow(targetY - shipY, 2);
        double disc = Math.pow(b, 2) - 4 * a * c;
        if (disc < 0) {
            return targetPosition;
        }
        double t1 = (-b + Math.sqrt(disc)) / (2 * a);
        double t2 = (-b - Math.sqrt(disc)) / (2 * a);
        double t;
        // find the least positive t
        if (t1 > 0) {
            if (t2 > 0) t = Math.min(t1, t2);
            else t = t1;
        } else {
            t = t2;
        }
        // multiply time by the target's velocity to get how far it travels
        double aimX = t * targetVelX + targetX;
        double aimY = t * targetVelY + targetY;
        return new Position(aimX, aimY);
    }

    /**
     * Find the maximum distance between two objects in the given space
     *
     * @param space physics
     * @return The maximum distance in the space
     */
    static double maxDistance(Toroidal2DPhysics space) {
        // Since the space wraps around, the furthest distance is from the center to a corner
        return Math.sqrt(Math.pow(space.getHeight(), 2) + Math.pow(space.getWidth(), 2)) / 2;
    }

    /**
     * Find the closest of a collection of objects to a given position
     *
     * @param space           physics
     * @param currentPosition The position from which to base the distance to the objects
     * @param objects         The collection of objects to measure
     * @param <T>             The type of objects
     * @return The object in objects that is closest to currentPosition
     */
    static <T extends AbstractObject> T closest(Toroidal2DPhysics space, Position currentPosition,
                                                Collection<T> objects) {
        T closest = null;
        double minimum = Double.MAX_VALUE;
        for (T object : objects) {
            Position interceptPosition = interceptPosition(space, object.getPosition(), currentPosition);
            double distance = space.findShortestDistance(currentPosition, interceptPosition);
            if (distance < minimum) {
                minimum = distance;
                closest = object;
            }
        }
        return closest;
    }

}
