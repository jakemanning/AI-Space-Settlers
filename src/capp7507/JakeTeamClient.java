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
    public static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
    private Map<UUID, UUID> currentTargets = new HashMap<>();
    private Map<UUID, Boolean> isAvoiding = new HashMap<>();
    private Map<UUID, CircleGraphics> targetGraphics = new HashMap<>();
    private Map<UUID, CircleGraphics> obstacleGraphics = new HashMap<>();

    /**
     * Converts from a linear scale from x1 to x2 to logarithmic scale from y1 to y2
     * <p>
     * For example, if the linear scale is from 0 to 90, and the logarithmic scale is 0 to 1,
     * then an input will be converted from the linear scale to the logarithmic scale
     *
     * @param oldMin Linear scale start
     * @param newMin Logarithmic scale start
     * @param oldMax Linear scale end
     * @param newMax Logarithmic scale end
     * @param input  What we want to convert
     * @return Logarithmic integer from y1 to y2
     */
    private static double logNormalize(double oldMin, double newMin, double oldMax, double newMax, double input) {
        if (newMin == 0) {
            newMin = 0.00001;
        }
        if (input < oldMin) {
            input = oldMin;
        } else if (input > oldMax) {
            input = oldMax;
        }

        double b = Math.log1p(newMax / newMin) / (oldMax - oldMin);
        double a = newMax / Math.exp(b * oldMax);

        return a * Math.exp(b * input);
    }

    /**
     * Converts from a linear scale from x1 to x2 to linear scale from y1 to y2
     * <p>
     * For example, if the first linear scale is from 0 to 1, and the linear scale is 1 to 90,
     * then an input will be converted from the first linear scale to the second linear scale (adhering to the original ratio)
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
                if (targetGraphic != null) graphics.add(targetGraphic);
                if (obstacleGraphic != null) graphics.add(obstacleGraphic);

                AbstractObject target = space.getObjectById(currentTargets.get(ship.getId()));
                if (target == null || !target.isAlive()) {
                    target = bestValue(space, ship, space.getAllObjects());
                    currentTargets.put(ship.getId(), target.getId());
                }
                Position targetPos = target.getPosition();
                targetGraphics.put(ship.getId(), new CircleGraphics(2, Color.RED, targetPos));
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                int shipRadius = ship.getRadius();
                AbstractObject obstruction = obstructionInPath(space, shipPos, targetPos, obstructions, shipRadius);
                AbstractAction action;
                if (obstruction != null) {
                    action = avoidCrashAction(space, obstruction, ship);
                    obstacleGraphics.put(ship.getId(), new CircleGraphics(2, Color.YELLOW, obstruction.getPosition()));
                } else {
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

    private AbstractObject bestValue(Toroidal2DPhysics space, Ship ship,
                                     Collection<AbstractObject> objects) {
        AbstractObject best = null;
        double maximum = Double.MIN_VALUE;
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
                if (isEnemyTarget(actionableObject)) {
                    value = energyValue(actionableObject);
                } else if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
                    if (gameIsEnding(space)) {
                        value = value + 100;
                    }
                } else if (actionableObject.getId() == ship.getId()) {
                    continue;
                }
            } else if (object instanceof Beacon) {
                value = energyValue(ship);
            }
            Set<AbstractObject> obstructions = getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                value = value * OBSTRUCTED_PATH_PENALTY;
            }
            double score = value / scaledDistance;
            if (score > maximum) {
                maximum = score;
                best = object;
            }
        }
        return best;
    }

    private double scaleDistance(double rawDistance, Toroidal2DPhysics space) {
        // Since the space wraps around, the furthest distance is from the center to a corner
        double maxDistance = Math.sqrt(Math.pow(space.getHeight(), 2) + Math.pow(space.getWidth(), 2)) / 2;
        double scaledDistance = linearNormalize(0, 0, maxDistance, 1, maxDistance - rawDistance);
        return 1 - scaledDistance;
    }

    private double angleValue(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        Position currentPosition = ship.getPosition();
        Position targetPosition = target.getPosition();
        Vector2D currentDirection = currentPosition.getTranslationalVelocity();
        double currentAngle = currentDirection.getAngle();
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, targetPosition);
        double targetAngle = targetDirection.getAngle();
        double angleDiff = Math.abs(currentAngle - targetAngle);
        return linearNormalize(0, 0, Math.PI / 2, 1, angleDiff);
    }

    private boolean isEnemyTarget(AbstractActionableObject actionableObject) {
        if (actionableObject instanceof Base) {
            Base base = (Base) actionableObject;
            if (base.isHomeBase()) {
                return false;
            }
        }
        return !actionableObject.getTeamName().equals(getTeamName());
    }

    private boolean isOurBase(AbstractActionableObject actionableObject) {
        return actionableObject instanceof Base && actionableObject.getTeamName().equals(getTeamName());
    }

    private double energyValue(AbstractActionableObject ship) {
        double missingEnergy = ship.getMaxEnergy() - ship.getEnergy();
        return linearNormalize(0, 0, ship.getMaxEnergy(), 2, missingEnergy);
    }

    private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return linearNormalize(0, 0, SHIP_MAX_RESOURCES, 2.5, total);
    }

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
}
