package capp7507;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.TargetGraphics;
import spacesettlers.objects.*;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.util.*;

/**
 * A model-based reflex agent for controlling a spacesettlers team client
 *
 * The ships use the bestValue function while keeping track of targets between
 * calls to getMovementStart. It assigns values based on distance from a ship to a target,
 * angle between ship and target, asteroid resources, energy value, obstructions. It also
 * factors in the target with the highest scoring neighbors around it. If an obstruction
 * is found between the ship and the target, then the ship attempts to avoid it.
 *
 * @author Jake Manning and Bryan Capps
 */
public class JakeTeamClient extends BryanTeamClient {
	private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
    private static final int SHIP_ENERGY_VALUE_WEIGHT = 6;
    private static final int SHIP_CARGO_VALUE_WEIGHT = 6;
    private static final double MAX_ANGLE = Math.PI / 2;
    private static final int REALLY_BIG_NAV_WEIGHT = 100;
    private static final int NEIGHBORHOOD_RADIUS = 100;
    private static final int MAX_OBSTRUCTION_DETECTION = 100;
    private Map<UUID, UUID> currentTargets = new HashMap<>();
    private Map<UUID, TargetGraphics> targetGraphics = new HashMap<>();
    private Map<UUID, CircleGraphics> obstacleGraphics = new HashMap<>();

	/**
	 * Called before movement begins. Fill a HashMap with actions depending on the bestValue
	 *
	 * @param space physics
	 * @param actionableObjects objects that can perform an action
	 * @return HashMap of actions to take per object id
	 */
	@Override
    public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
                                                      Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, AbstractAction> actions = new HashMap<>();

        for (AbstractActionableObject actionable :  actionableObjects) {

            Position shipPos = actionable.getPosition();

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                TargetGraphics targetGraphic = targetGraphics.get(ship.getId());
                CircleGraphics obstacleGraphic = obstacleGraphics.get(ship.getId());
                if (targetGraphic != null) graphics.add(targetGraphic);
                if (obstacleGraphic != null) graphics.add(obstacleGraphic);

                AbstractObject target = space.getObjectById(currentTargets.get(ship.getId()));
                if (target == null || !target.isAlive()) {
                    target = bestValue(space, ship, space.getAllObjects());
                    currentTargets.put(ship.getId(), target.getId());
                }
                Position targetPos = target.getPosition();
                targetGraphics.put(ship.getId(), new TargetGraphics(8, targetPos));
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                AbstractObject obstruction = obstructionInPath(space, shipPos, targetPos, obstructions, ship.getRadius());
                AbstractAction action;
                if (obstruction != null) { // There is an obstacle to avoid
                    action = avoidCrashAction(space, obstruction, target, ship);
                    obstacleGraphics.put(ship.getId(), new CircleGraphics(2, Color.YELLOW, obstruction.getPosition()));
                } else { // We can just go straight to the object
                    obstacleGraphics.remove(ship.getId());
                    action = getMoveAction(space, shipPos, target);
                }
                actions.put(ship.getId(), action);
            } else if (actionable instanceof Base) {
                Base base = (Base) actionable;
                actions.put(base.getId(), new DoNothingAction());
            }
        }

        return actions;
    }

	/**
	 * Determine the best object to navigate towards based on highest score.
	 * Scored on:
	 * - Distance: Distance between target (which is closest?)
	 * - Ships resources/energy: Cargo value and energy value to choose between bases/beacons
	 * - Asteroids: Asteroid mass and which has the highest scoring neighbors
	 * - Obstacles: Score is halved if there are obstacles between it and target
	 *
	 * @param space physics
	 * @param ship current ship
	 * @param objects from which we determine which object to head to
	 * @return best object based on our heuristics (highest total score)
	 */
    private AbstractObject bestValue(Toroidal2DPhysics space, Ship ship,
                                     Collection<AbstractObject> objects) {
        Map<UUID, Double> scores = new HashMap<>();
        for (AbstractObject object : objects) {
            double value = 0;
            if (object instanceof Asteroid) {
                Asteroid asteroid = (Asteroid) object;
                if (asteroid.isMineable()) {
                    value += linearNormalize(MIN_ASTEROID_MASS, 0, MAX_ASTEROID_MASS, 1, asteroid.getMass());
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isOurBase(actionableObject)) {
                    value += energyValue(ship) + cargoValue(ship);
                    if (gameIsEnding(space)) {
                        value += REALLY_BIG_NAV_WEIGHT; // We really want to go back to a base and deposit resources
                    }
                } else if (actionableObject.getId() == ship.getId()) {
                    continue; // Don't ever set the target to our current ship
                }
            } else if (object instanceof Beacon) {
                value += energyValue(ship);
            }
            Set<AbstractObject> obstructions = getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                value *= OBSTRUCTED_PATH_PENALTY; // We should be less likely to go towards objects with obstacles in the way
            }

            Position adjustedObjectPosition = interceptPosition(space, object.getPosition(), ship.getPosition());
            double rawDistance = space.findShortestDistance(ship.getPosition(), adjustedObjectPosition);
            double scaledDistance = scaleDistance(space, rawDistance);
            scaledDistance += angleValue(space, ship, object);

            double score = value / scaledDistance;
            scores.put(object.getId(), score);
        }
        // Calculate neighbor scores for all objects, finding the highest density of asteroids to head to
        for (AbstractObject object : objects) {
            if (!(object instanceof Asteroid) || (scores.getOrDefault(object.getId(), 0.0) == 0)) {
                continue;
            }
            double score = scores.getOrDefault(object.getId(), 0.0);
            score += neighborScores(space, scores, object);
            scores.put(object.getId(), score);
        }
        // Find the object with the highest score
        Map.Entry<UUID, Double> maxEntry = Collections.max(scores.entrySet(), Comparator.comparing(Map.Entry::getValue));
        return space.getObjectById(maxEntry.getKey());
    }

    /**
     * Linearly normalizes the distance from 0 to 1
     *
     * @param space physics
     * @param rawDistance Input distance to convert
     * @return normalized distance, preserving ratio from 0 to 1
     */
    private double scaleDistance(Toroidal2DPhysics space, double rawDistance) {
        double maxDistance = maxDistance(space);
        double scaledDistance = linearNormalize(0, 0, maxDistance, 1, maxDistance - rawDistance);
        return 1 - scaledDistance;
    }

    /**
     * Value for an angle between your ship and a provided object
     *
     * @param space physics
     * @param ship ship the angle is from
     * @param target object the angle is to
     * @return Linear normalized value from 0 to 1 based a target
     */
    private double angleValue(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        Position currentPosition = ship.getPosition();
        Position targetPosition = target.getPosition();
        Vector2D currentDirection = currentPosition.getTranslationalVelocity();
        double currentAngle = currentDirection.getAngle();
        Position adjustedTargetPosition = interceptPosition(space, targetPosition, currentPosition);
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition);
        double targetAngle = targetDirection.getAngle();
        double angleDiff = Math.abs(currentAngle - targetAngle);
        return linearNormalize(0, 0, MAX_ANGLE, 1, angleDiff);
    }

    /**
     * Determines whether a given actionableObject is on your team
     *
     * @param actionableObject object to check
     * @return Whether the object is a base on your team
     */
    private boolean isOurBase(AbstractActionableObject actionableObject) {
        return actionableObject instanceof Base && actionableObject.getTeamName().equals(getTeamName());
    }

    /**
     * Determines the ship's energyValue based on how low the ship's energy is
     * Linear normalizes the value of your energy from 0 to a constant
     *
     * @param ship ship to calculate energyValue
     * @return Higher value if ship's energy level is low
     */
    private double energyValue(AbstractActionableObject ship) {
        double missingEnergy = ship.getMaxEnergy() - ship.getEnergy();
        return linearNormalize(0, 0, ship.getMaxEnergy(), SHIP_ENERGY_VALUE_WEIGHT, missingEnergy);
    }

    /**
     * Determines the ship's cargoValue based on how many resources the ship has
     *
     * @param ship ship to fetch cargo value
     * @return Higher value if ship has a lot of resources
     */
    private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return linearNormalize(0, 0, SHIP_MAX_RESOURCES, SHIP_CARGO_VALUE_WEIGHT, total);
    }

	/**
	 * Continually add scores to a map based on best neighborhood score
	 *
	 * @param space physics
	 * @param scores scores map to fill
	 * @param object object to compare neighbors
	 * @return the best neighbor score for the given object
	 */
    private double neighborScores(Toroidal2DPhysics space, Map<UUID, Double> scores, AbstractObject object) {
        double total = 0;
        for (UUID uuid : scores.keySet()) {
            AbstractObject neighbor = space.getObjectById(uuid);
            // TODO: Eventually ensure the angle to turn is accounted (should be more likely to go towards objects in a line)
            if (space.findShortestDistance(object.getPosition(), neighbor.getPosition()) > NEIGHBORHOOD_RADIUS) {
                continue;
            }
            total += scores.getOrDefault(neighbor.getId(), 0.0);
        }
        // Ensure score is differentiated between neighbors and object (otherwise all neighbors would have same score)
        return total / 2;
    }

	/**
	 * Remove inconsistent objects from our space if died or if objects were removed
	 *
	 * @param space physics
	 * @param actionableObjects current actionable objects we are working with
	 */
	@Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
        for (Map.Entry<UUID, UUID> entry : currentTargets.entrySet()) {
            UUID shipId = entry.getKey();
            AbstractObject target = space.getObjectById(entry.getValue());
            AbstractObject ship = space.getObjectById(shipId);
            double distance = space.findShortestDistance(ship.getPosition(), target.getPosition());
            int targetRadius = target.getRadius();
            boolean closeEnough = distance < targetRadius * 3;
            if (!target.isAlive() || space.getObjectById(target.getId()) == null || closeEnough) {
                currentTargets.remove(shipId);
            }
        }
    }

	/**
	 * Determine if there is an obstruction, ignoring obstructions greater than MAX_OBSTRUCTION_DETECTION
	 *
	 * @param space physics
	 * @param startPosition the starting location of the straight line path
	 * @param goalPosition  the ending location of the straight line path
	 * @param obstructions  an Set of AbstractObject obstructions (i.e., if you don't wish to consider mineable asteroids or beacons obstructions)
	 * @param freeRadius    used to determine free space buffer size
	 * @return obstruction, if exists and less than MAX_OBSTRUCTION_DETECTION
	 */
    @Override
    AbstractObject obstructionInPath(Toroidal2DPhysics space, Position startPosition,
                                     Position goalPosition, Set<AbstractObject> obstructions, int freeRadius) {
        AbstractObject obstruction = super.obstructionInPath(space, startPosition,
                goalPosition, obstructions, freeRadius);
        if (obstruction == null || space.findShortestDistance(startPosition, obstruction.getPosition()) > MAX_OBSTRUCTION_DETECTION) {
            return null; // No obstruction
        } else {
            return obstruction;
        }
    }

    /**
     * Converts from a linear scale from x1 to x2 to linear scale from y1 to y2
     * For example, if the first linear scale is from 0 to 1, and the linear scale is 1 to 90,
     * then an input will be converted from the first linear scale to the second linear scale (adhering to the original ratio)
	 *
	 * For example first range is (0.1 to 0.6), and second range is (0.7 to 1.2).
     * Input of 0.3 will return 0.9, the ratio of the input between the first range, normalized to the second range
     *
     * @param oldMin Original Linear scale start
     * @param newMin New linear scale start
     * @param oldMax Original scale end
     * @param newMax New linear scale end
     * @param input  What we want to convert
     * @return Linearly scaled integer from old range to new range
     */
    private static double linearNormalize(double oldMin, double newMin, double oldMax, double newMax, double input) {
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
}
