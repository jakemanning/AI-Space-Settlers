package capp7507;

import spacesettlers.actions.AbstractAction;
import spacesettlers.actions.DoNothingAction;
import spacesettlers.objects.*;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

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
    private static final int SHIP_ENERGY_WEIGHT = 600;
    private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private Map<UUID, AbstractObject> currentTargets = new HashMap<>();

    @Override
    public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
                                                      Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, AbstractAction> actions = new HashMap<>();

        for (AbstractActionableObject actionable :  actionableObjects) {

            Position shipPos = actionable.getPosition();

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                AbstractAction currentAction = ship.getCurrentAction();
                if (currentAction != null && !currentAction.isMovementFinished(space)) {
                    if (!(currentAction instanceof AvoidAction)) {
                        currentTargets.remove(ship.getId());
                    }
                    actions.put(ship.getId(), currentAction);
                    continue;
                }
                // We need a new action
                // Use the current target or get a new one
                AbstractObject target = currentTargets.get(ship.getId());
                if (target == null) {
                    target = bestValue(space, ship, space.getAllObjects());
                    currentTargets.put(ship.getId(), target);
                }
                Position targetPos = target.getPosition();
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                int shipRadius = ship.getRadius();
                AbstractObject obstruction = obstructionInPath(space, shipPos, targetPos, obstructions, shipRadius);
                AbstractAction action;
                if (obstruction != null) {
                    action = avoidCrashAction(space, obstruction, ship);
                } else {
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

            double scaledDistance = Math.log1p(rawDistance);
            scaledDistance = scaledDistance + angleValue(space, ship, object);
            double value = 0;
            if (object instanceof Asteroid) {
                Asteroid asteroid = (Asteroid) object;
                if (asteroid.isMineable()) {
                    value = asteroid.getMass();
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isEnemyTarget(actionableObject)) {
                    value = energyValue(actionableObject);
                } else if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
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

    private double angleValue(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        Position currentPosition = ship.getPosition();
        Position targetPosition = target.getPosition();
        Vector2D currentDirection = currentPosition.getTranslationalVelocity();
        double currentAngle = currentDirection.getAngle();
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, targetPosition);
        double targetAngle = targetDirection.getAngle();
        return Math.abs(currentAngle - targetAngle);
    }

    /**
     * Converts from a linear scale from x1 to x2 to logarithmic scale from y1 to y2
     *
     * For example, if the linear scale is from 0 to 90, and the logarithmic scale is 0 to 1,
     * then an input will be converted from the linear scale to the logarithmic scale
     * @param oldMin Linear scale start
     * @param newMin Logarithmic scale start
     * @param oldMax Linear scale end
     * @param newMax Logarithmic scale end
     * @param input What we want to convert
     * @return Logarithmic integer from y1 to y2
     */
    private static double logNormalize(double oldMin, double newMin, double oldMax, double newMax, double input) {
        if(input < oldMin) { input = oldMin; }
        else if(input > newMax) { input = newMin; }

        double b = Math.log1p(newMin / newMax) / (oldMin - oldMax);
        double a = newMin / Math.exp(b * oldMin);

        return a * Math.exp(b * input);
    }

    /**
     * Converts from a linear scale from x1 to x2 to linear scale from y1 to y2
     *
     * For example, if the first linear scale is from 0 to 1, and the linear scale is 1 to 90,
     * then an input will be converted from the first linear scale to the second linear scale (adhering to the original ratio)
     * @param oldMin Original Linear scale start
     * @param newMin New linear scale start
     * @param oldMax Original scale end
     * @param newMax New linear scale end
     * @param input What we want to convert
     * @return Linearly scaled integer from old range to new range
     */
    private static double linearNormalize(double oldMin, double newMin, double oldMax, double newMax, double input) {
        if(input < oldMin) { input = oldMin; }
        else if(input > newMax) { input = newMin; }

        double oldRange = oldMax - oldMin;
        if (oldRange == 0) {
            return newMin;
        } else {
            double newRange = newMax - newMin;
            return (((input - oldMin) * newRange) / oldRange) + newMin;
        }
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
        double percentEnergy = ship.getEnergy() / ship.getMaxEnergy();
        return SHIP_ENERGY_WEIGHT / (percentEnergy + 0.001 /* add a bit to avoid dividing by zero */);
    }

    private double cargoValue(Ship ship) {
        return ship.getResources().getTotal();
    }
}
