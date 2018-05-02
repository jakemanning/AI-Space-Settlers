package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.*;
import java.util.stream.Collectors;


/**
 * A model-based reflex agent for controlling a spacesettlers team client
 * <p>
 * The ships use the bestValue function while keeping track of targets between
 * calls to getMovementStart. It assigns values based on distance from a ship to a target,
 * angle between ship and target, asteroid resources, energy value, obstructions. It also
 * factors in the target with the highest scoring neighbors around it. If an obstruction
 * is found between the ship and the target, then the ship attempts to avoid it.
 *
 * @author Jake Manning and Bryan Capps
 */
public class JakeTeamClient extends TeamClient {
    private static final boolean DEBUG = true;
    private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
    static final double TARGET_SHIP_SPEED = 60;
    private static final int SHIP_ENERGY_VALUE_WEIGHT = 8;
    private static final int SHIP_CARGO_VALUE_WEIGHT = 6;
    private static final double MAX_ANGLE = Math.PI / 2;
    private static final int REALLY_BIG_NAV_WEIGHT = 100;
    private static final int NEIGHBORHOOD_RADIUS = 100;
    private static final double GAME_IS_ENDING_FACTOR = 0.98;
    private Map<UUID, Route> currentRoutes = new HashMap<>();
    private GraphicsUtil graphicsUtil;
    private PowerupUtil powerupUtil;
    private PlanningUtil planningUtil;

    /**
     * Called before movement begins. Fill a HashMap with actions depending on the bestValue
     *
     * @param space             physics
     * @param actionableObjects objects that can perform an action
     * @return HashMap of actions to take per object id
     */
    @Override
    public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
                                                      Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, AbstractAction> actions = new HashMap<>();

        for (AbstractActionableObject actionable : actionableObjects) {

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                UUID shipId = ship.getId();
                Position shipPos = actionable.getPosition();
                graphicsUtil.loadGraphicsFor(shipId);

                // Retrieve ship's current target or pick a new one if needed
                Route currentRoute = currentRoutes.get(actionable.getId());
                if (currentRoute == null
                        || currentRoute.isDone()
                        || pathBlocked(space, ship, currentRoute.getStep(), currentRoute.getGoal())) {
                    AbstractObject target;
                    ShipRole role = planningUtil.getRole(ship);
                    if (role == ShipRole.FLAGGER) {
                        if (ship.isCarryingFlag()) {
                            Set<AbstractObject> ourBases = space.getBases().stream()
                                    .filter(this::isOurBase)
                                    .collect(Collectors.toSet());
                            ourBases.removeAll(planningUtil.otherShipGoals(ship));
                            target = MovementUtil.closest(space, shipPos, ourBases);
                        } else {
                            target = planningUtil.flagTarget(space, ship);
                        }
                    } else {
                        AbstractObject oldGoal = currentRoute == null ? null : currentRoute.getGoal();
                        Set<AbstractObject> objectsToEvaluate = space.getAllObjects();
                        objectsToEvaluate.removeAll(planningUtil.otherShipGoals(ship));
                        if (oldGoal != null) {
                            objectsToEvaluate.remove(oldGoal);
                        }
                        planningUtil.setRole(ship, ShipRole.MINER);
                        target = bestValue(space, ship, objectsToEvaluate);
                    }
                    currentRoute = AStar.forObject(target, ship, space);
                    currentRoutes.put(shipId, currentRoute);
                }
                Position currentStep = currentRoute.getStep();
                graphicsUtil.addTargetPreset(shipId, GraphicsUtil.Preset.TARGET, currentStep);
                currentRoute.getGraphics().forEach(graphicsUtil::addGraphic);

                if (currentStep == null) {
                    System.out.println(space.getCurrentTimestep()
                            + ": The search failed - guess we better give up for this time step");
                    actions.put(ship.getId(), new DoNothingAction());
                    continue;
                }

                Position nextStep = currentRoute.getNextStep();
                MoveAction action = getMoveAction(space, shipPos, currentStep, nextStep);
                action.setKvRotational(4);
                action.setKpRotational(4);
                action.setKvTranslational(2);
                action.setKpTranslational(1);
                actions.put(ship.getId(), action);
                graphicsUtil.removeObstacle(shipId);
            } else if (actionable instanceof Base) {
                Base base = (Base) actionable;
                actions.put(base.getId(), new DoNothingAction());
            }
        }

        return actions;
    }

    /**
     * Determine if there is an obstacle in the way of the ship
     *
     * @param space        physics
     * @param ship         The ship we are moving from
     * @param stepPosition the target of our path
     * @return true if an obstacle is in the way
     */
    private boolean pathBlocked(Toroidal2DPhysics space, Ship ship, Position stepPosition, AbstractObject target) {
        Set<AbstractObject> obstructions = SpaceSearchUtil.getObstructions(space, ship);
        obstructions.remove(target);
        return stepPosition != null && !space.isPathClearOfObstructions(ship.getPosition(), stepPosition,
                obstructions, ship.getRadius());
    }

    /**
     * Determine the best object to navigate towards based on highest score.
     * Scored on:
     * - Distance: Distance between target (which is closest?)
     * - Ships resources/energy: Cargo value and energy value to choose between bases/beacons
     * - Asteroids: Asteroid mass and which has the highest scoring neighbors
     * - Obstacles: Score is halved if there are obstacles between it and target
     *
     * @param space   physics
     * @param ship    current ship
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
                    value = MovementUtil.linearNormalize(MIN_ASTEROID_MASS, MAX_ASTEROID_MASS, 0, 1, asteroid.getMass());
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
                    if (gameIsEnding(space)) {
                        value += REALLY_BIG_NAV_WEIGHT; // We really want to go back to a base and deposit resources
                    }
                } else {
                    continue; // Don't ever set the target to our ships or other ships/bases
                }
            } else if (object instanceof Beacon) {
                value = energyValue(ship);
            }

            Set<AbstractObject> obstructions = SpaceSearchUtil.getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                value *= OBSTRUCTED_PATH_PENALTY; // We should be less likely to go towards objects with obstacles in the way
            }

            Position adjustedObjectPosition = MovementUtil.interceptPosition(space, object.getPosition(), ship.getPosition());
            double rawDistance = space.findShortestDistance(ship.getPosition(), adjustedObjectPosition);
            double scaledDistance = scaleDistance(space, rawDistance) + angleValue(space, ship, object);
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
     * Get a {@link MoveAction} that will take a ship through space from the current position to the target
     * <p>
     * This action attempts to move the ship to where the target will be in the future.
     * The ship and the target will collide even if the target is in motion at a constant rate.
     * This method uses interceptPosition(Toroidal2DPhysics, Position, Position) to predict the
     * target's future location.
     * The ship will be going a speed of {@value TARGET_SHIP_SPEED} units by default when it reaches the target, depending on
     * the angle it needs to turn to reach nextStep (slower if angle is larger), so it doesn't overshoot each target
     *
     * @param space           physics
     * @param currentPosition The position of the ship at the starting time interval
     * @param target          The target object the action should aim for
     * @param nextStep        the nextStep our route contains
     * @return An action to get the ship to the target's location
     */
    private MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, Position target, Position nextStep) {
        Position adjustedTargetPosition = MovementUtil.interceptPosition(space, target, currentPosition);
        Vector2D targetVector = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition);
        double magnitude;
        if (nextStep == null) {
            double angle = targetVector.angleBetween(currentPosition.getTranslationalVelocity());
            magnitude = MovementUtil.linearNormalizeInverse(0, Math.PI, 10, TARGET_SHIP_SPEED, angle);
        } else {
            Vector2D nextTargetVector = space.findShortestDistanceVector(target, nextStep);
            double nextGoalAngle = Math.abs(targetVector.angleBetween(nextTargetVector));
            magnitude = MovementUtil.linearNormalizeInverse(0, Math.PI, 15, TARGET_SHIP_SPEED, nextGoalAngle);
        }

        double goalAngle = targetVector.getAngle();
        Vector2D goalVelocity = Vector2D.fromAngle(goalAngle, magnitude);
        return new MoveAction(space, currentPosition, target, goalVelocity);
    }

    /**
     * Remove inconsistent objects from our space if died or if objects were removed
     * Also do things based on targets going away like re-planning when we capture a flag or take it back to a base
     *
     * @param space             physics
     * @param actionableObjects current actionable objects we are working with
     */
    @Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
        Set<UUID> routesToRemove = new HashSet<>();

        for (Map.Entry<UUID, Route> entry : currentRoutes.entrySet()) {
            UUID shipId = entry.getKey();
            Route route = entry.getValue();
            AbstractObject target = space.getObjectById(route.getGoal().getId());
            Ship ship = (Ship) space.getObjectById(shipId);
            Position shipPosition = ship.getPosition();
            double distance = space.findShortestDistance(shipPosition, target.getPosition());
            int targetRadius = target.getRadius();
            boolean closeEnough = (target instanceof Base) && distance < targetRadius + ship.getRadius() + 10;
            boolean flagAcquired = (target instanceof Flag) && ship.isCarryingFlag();

            // Handle when our target dies
            if (!target.isAlive() || space.getObjectById(target.getId()) == null || closeEnough || flagAcquired) {
                routesToRemove.add(shipId);
            }

            Position step = route.getStep();
            if (step != null && space.findShortestDistance(shipPosition, step) < ship.getRadius() * 1.5) {
                route.completeStep();
            }
        }

        for (UUID key : routesToRemove) {
            currentRoutes.remove(key);
        }

        planningUtil.assignClosestFlagCollectors(space);
    }

    /**
     * Whether the game is ending soon or not. Ships should return any resources before the game is over.
     *
     * @param space The Toroidal2DPhysics for the game
     * @return True if the game is nearly over, false otherwise
     */
    private boolean gameIsEnding(Toroidal2DPhysics space) {
        return space.getCurrentTimestep() > space.getMaxTime() * GAME_IS_ENDING_FACTOR;
    }

    // region Value Calculations

    /**
     * Linearly normalizes the distance from 0 to 1
     *
     * @param space       physics
     * @param rawDistance Input distance to convert
     * @return normalized distance, preserving ratio from 0 to 1
     */
    private double scaleDistance(Toroidal2DPhysics space, double rawDistance) {
        double maxDistance = MovementUtil.maxDistance(space);
        return MovementUtil.linearNormalizeInverse(0, maxDistance, 0, 1, maxDistance - rawDistance);
    }

    /**
     * Value for an angle between your ship and a provided object
     *
     * @param space  physics
     * @param ship   ship the angle is from
     * @param target object the angle is to
     * @return Linear normalized value from 0 to 1 based a target
     */
    private double angleValue(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        Position currentPosition = ship.getPosition();
        Position targetPosition = target.getPosition();
        Vector2D currentDirection = currentPosition.getTranslationalVelocity();
        double currentAngle = currentDirection.getAngle();
        Position adjustedTargetPosition = MovementUtil.interceptPosition(space, targetPosition, currentPosition);
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition);
        double targetAngle = targetDirection.getAngle();
        double angleDiff = Math.abs(currentAngle - targetAngle);
        return MovementUtil.linearNormalize(0, MAX_ANGLE, 0, 1, angleDiff);
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
        return MovementUtil.linearNormalize(0, ship.getMaxEnergy(), 0, SHIP_ENERGY_VALUE_WEIGHT, missingEnergy);
    }

    /**
     * Determines the ship's cargoValue based on how many resources the ship has
     *
     * @param ship ship to fetch cargo value
     * @return Higher value if ship has a lot of resources
     */
    private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return MovementUtil.linearNormalize(0, SHIP_MAX_RESOURCES, 0, SHIP_CARGO_VALUE_WEIGHT, total);
    }

    /**
     * Continually add scores to a map based on best neighborhood score
     *
     * @param space  physics
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
    // endregion

    /**
     * Get the team's purchases for this turn
     * Buys bases and tries to spread them out in the space. Also buys double max energy powerups.
     * It buys whenever it can afford to.
     *
     * @param space              physics
     * @param actionableObjects  Ships and bases on the team
     * @param resourcesAvailable how much resourcesAvailable you have
     * @param purchaseCosts      Used to see if we can afford to buy things
     * @return A map of ship or base IDs to types of purchases we want to buy
     */
    @Override
    public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                     Set<AbstractActionableObject> actionableObjects,
                                                     ResourcePile resourcesAvailable,
                                                     PurchaseCosts purchaseCosts) {
        // We need to do some more before we can actually purchase some stuff

//        return powerupUtil.getTeamPurchases(space, actionableObjects, resourcesAvailable, purchaseCosts);
        return new HashMap<>();
    }

    /**
     * Shoot at other ships and use powerups.
     * <p>
     * We shoot at any enemy ships or bases if we are in
     * position according to 'inPositionToShoot'.
     *
     * @param space             physics
     * @param actionableObjects the ships and bases for this team
     * @return A map from ship IDs to powerup types
     */
    public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                           Set<AbstractActionableObject> actionableObjects) {
        return powerupUtil.getPowerups(space, actionableObjects);
    }

    // region Boilerplate
    @Override
    public void initialize(Toroidal2DPhysics space) {
        graphicsUtil = new GraphicsUtil(DEBUG);
        powerupUtil = new PowerupUtil(this, space, random);
        planningUtil = new PlanningUtil(getTeamName());
    }

    @Override
    public void shutDown(Toroidal2DPhysics space) {
        powerupUtil.shutDown();
    }

    @Override
    public Set<SpacewarGraphics> getGraphics() {
        return graphicsUtil.getGraphics();
    }
    // endregion
}
