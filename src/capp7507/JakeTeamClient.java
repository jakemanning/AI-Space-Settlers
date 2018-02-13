package capp7507;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.graphics.CircleGraphics;
import spacesettlers.objects.*;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.util.*;

/**
 * A team of alright agents
 *
 * The agents move towards things with the "best value" and shoots when it gets close to enemy ships.
 * If it's low on energy then it goes to the nearest beacon or friendly base.
 * @author Bryan Capps
 *
 */
public class JakeTeamClient extends BryanTeamClient {
	private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
	private static final double MAX_ANGLE = Math.PI / 2;
	private static final int REALLY_BIG_NAV_WEIGHT = 100;
	private static final int NEIGHBORHOOD_RADIUS = 100;
	private static final int MAX_OBSTRUCTION_DETECTION = 100;
	private Map<UUID, UUID> currentTargets = new HashMap<>();
    private Map<UUID, CircleGraphics> targetGraphics = new HashMap<>();
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
                CircleGraphics targetGraphic = targetGraphics.get(ship.getId());
                CircleGraphics obstacleGraphic = obstacleGraphics.get(ship.getId());
                if (DEBUG && targetGraphic != null) graphics.add(targetGraphic);
                if (DEBUG && obstacleGraphic != null) graphics.add(obstacleGraphic);

                AbstractObject target = space.getObjectById(currentTargets.get(ship.getId()));
                if (target == null || !target.isAlive()) {
                    target = bestValue(space, ship, space.getAllObjects());
                    currentTargets.put(ship.getId(), target.getId());
                }
                Position targetPos = target.getPosition();
                if(DEBUG) {
					targetGraphics.put(ship.getId(), new CircleGraphics(2, Color.RED, targetPos));
				}
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                int shipRadius = ship.getRadius();
                AbstractObject obstruction = obstructionInPath(space, shipPos, targetPos, obstructions, shipRadius);
                AbstractAction action;
                if (obstruction != null) {
                    action = avoidCrashAction(space, obstruction, target, ship);
                    if(DEBUG) {
						obstacleGraphics.put(ship.getId(), new CircleGraphics(2, Color.YELLOW, obstruction.getPosition()));
					}
                } else {
                	if(DEBUG) {
						obstacleGraphics.remove(ship.getId());
					}
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
	 * Linearly normalizes the distance from 0 to 1
	 *
	 * @param rawDistance Input distance to convert
	 * @param space physics
	 * @return normalized distance, preserving ratio from 0 to 1
	 */
    private double scaleDistance(double rawDistance, Toroidal2DPhysics space) {
        // Since the space wraps around, the furthest distance is from the center to a corner
        double maxDistance = Math.sqrt(Math.pow(space.getHeight(), 2) + Math.pow(space.getWidth(), 2)) / 2;
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
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, targetPosition);
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
	 * Determine the best object to navigate towards based on score.
	 * Scored on:
	 * - Distance: Distance between target (which is closest?)
	 * - Ships resources/energy: Cargo value and energy value to choose between bases/beacons
	 * - Asteroids: Asteroid mass and which has the highest scoring neighbors
	 * - Obstacles: Score is halved if there are obstacles between it and target
	 *
	 * @param space physics
	 * @param ship current ship
	 * @param objects from which we determine which object to head to
	 * @return best object based on our heuristics
	 */
    private AbstractObject bestValue(Toroidal2DPhysics space, Ship ship,
                                     Collection<AbstractObject> objects) {
        Map<UUID, Double> scores = new HashMap<>();
        for (AbstractObject object : objects) {
            double rawDistance = space.findShortestDistance(ship.getPosition(), object.getPosition());

            double scaledDistance = scaleDistance(rawDistance, space);
            scaledDistance = scaledDistance + angleValue(space, ship, object);
            double value = 0;
            if (object instanceof Asteroid) {
                Asteroid asteroid = (Asteroid) object;
                if (asteroid.isMineable()) {
                    value = linearNormalize(MIN_ASTEROID_MASS, 0, MAX_ASTEROID_MASS, 1, asteroid.getMass());
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
                    if (gameIsEnding(space)) {
                        value = value + REALLY_BIG_NAV_WEIGHT; // We really want to go back to a base and deposit resources
                    }
                } else if (actionableObject.getId() == ship.getId()) {
                    continue; // Don't ever set the target to our current ship
                }
            } else if (object instanceof Beacon) {
                value = energyValue(ship);
            }
            Set<AbstractObject> obstructions = getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                value = value * OBSTRUCTED_PATH_PENALTY;
            }
            double score = value / scaledDistance;
            scores.put(object.getId(), score);
        }
        // Calculate neighbor scores for all objects, finding the highest density of asteroids to head to
        for (AbstractObject object : objects) {
            if (!(object instanceof Asteroid) || (scores.getOrDefault(object.getId(), 0.0) == 0)) {
                continue;
            }
            double score = scores.getOrDefault(object.getId(), 0.0);
            scores.put(object.getId(), score + neighborScores(space, scores, object));
        }
        // Find the object with the most nearby neighbors
        Map.Entry<UUID, Double> maxEntry = Collections.max(scores.entrySet(), Comparator.comparing(Map.Entry::getValue));
        return space.getObjectById(maxEntry.getKey());
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
            if (space.findShortestDistance(object.getPosition(), neighbor.getPosition()) > NEIGHBORHOOD_RADIUS) {
                continue;
            }
            total += scores.getOrDefault(neighbor.getId(), 0.0);
        }
        // Ensure score is differentiated between neighbors and object (otherwise all neighbors would have same score)
        return total / 2;
    }

	/**
	 * Linear normalizes the value of your energy from 0 to a constant
	 *
	 * @param ship ship to calculate energyValue
	 * @return linear normalized energy value
	 */
	private double energyValue(AbstractActionableObject ship) {
        double missingEnergy = ship.getMaxEnergy() - ship.getEnergy();
        return linearNormalize(0, 0, ship.getMaxEnergy(), 6, missingEnergy);
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
	 * Linearly normalized value of cargo from input ship
	 *
	 * @param ship ship to fetch cargo value
	 * @return linear normalized from 0 to a constant
	 */
	private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return linearNormalize(0, 0, SHIP_MAX_RESOURCES, 6, total);
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
