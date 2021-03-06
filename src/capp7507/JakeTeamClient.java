package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.ImmutableTeamInfo;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.*;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static capp7507.MovementUtil.*;
import static capp7507.SpaceSearchUtil.*;
import static capp7507.TrainingPowerupUtil.MAX_SHOOT_DISTANCE;

/**
 * A model-based reflex agent for controlling a spacesettlers team client
 * <p>
 * Set TRAINING_GA to true to train the genetic algorithm for avoiding asteroids
 * Set TRAINING_TREE to true to collect data about shooting other ships
 * <p>
 * The ships use the bestValue function while keeping track of targets between
 * calls to getMovementStart. It assigns values based on distance from a ship to a target,
 * angle between ship and target, asteroid resources, energy value, obstructions. It also
 * factors in the target with the highest scoring neighbors around it. The ships use
 * A* algorithm to determine the best path to a given target, choosing a path with no obstacles in way.
 *
 * @author Jake Manning and Bryan Capps
 */
public class JakeTeamClient extends TeamClient {

    private static Level LOG_LEVEL = Level.INFO;
    private static final boolean DEBUG_GRAPHIC = true;
    private static final boolean TRAINING_GA = false;
    static final boolean TRAINING_TREE = false;
    private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
    static final double TARGET_SHIP_SPEED = 60;
    private static final int SHIP_ENERGY_VALUE_WEIGHT = 6;
    private static final int SHIP_CARGO_VALUE_WEIGHT = 6;
    private static final double MAX_ANGLE = Math.PI / 2;
    private static final int REALLY_BIG_NAV_WEIGHT = 100;
    private static final int NEIGHBORHOOD_RADIUS = 100;
    private static final double GAME_IS_ENDING_FACTOR = 0.98;
    private static final int SHOT_TRAINING_TARGET_WEIGHT = 3;
    private Map<UUID, UUID> currentTargets = new HashMap<>();
    private GraphicsUtil graphicsUtil;
    private PowerupUtil powerupUtil;
    private KnowledgeUtil knowledge;
    private int shotDistance;
    private Logger logger = Logger.getLogger(JakeTeamClient.class.getName());

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
            powerupUtil.shieldIfNeeded(space, actionable);

            Position shipPos = actionable.getPosition();

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                graphicsUtil.loadGraphicsFor(ship.getId());
                SessionCollection sessionCollection;
                if(TRAINING_GA) {
                    sessionCollection = knowledge.getSessionsFor(ship.getId());
                }

                // Retrieve ship's current target or pick a new one if needed
                AbstractObject target = space.getObjectById(currentTargets.get(ship.getId()));
                if (target == null || !target.isAlive()) {
                    target = bestValue(space, ship, space.getAllObjects());
                    currentTargets.put(ship.getId(), target.getId());
                }
                Position targetPos = target.getPosition();
                graphicsUtil.addTargetPreset(ship.getId(), GraphicsUtil.Preset.TARGET, targetPos);

                // Look for an obstruction in the way to target and avoid if one is found
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                int shipRadius = ship.getRadius();
                AbstractObject obstruction = obstructionInPath(space, shipPos, targetPos, obstructions, shipRadius);
                AbstractAction action;
                if (obstruction != null) {
                    // Begin/keep avoiding
                    action = avoidCrashAction(space, obstruction, target, ship);
                    graphicsUtil.addObstaclePreset(ship.getId(), GraphicsUtil.Preset.YELLOW_CIRCLE, obstruction.getPosition());
                } else {
                    // Move towards goal, no more avoiding the issue at hand
                    graphicsUtil.removeObstacle(ship.getId());

                    if (TRAINING_GA) {
                        // We aren't avoiding anything anymore. We done'
                        ImmutableTeamInfo currentInfo = getTeamInfo(space, getTeamName());
                        double score = currentInfo == null ? 0.0 : currentInfo.getScore();
                        sessionCollection.completeAllSessions(space, ship, score);
                    }

                    if (target instanceof AbstractActionableObject && !isOurBase((AbstractActionableObject) target)) {
                        action = getMoveActionToShoot(space, shipPos, target.getPosition());
                    } else {
                        action = getMoveAction(space, shipPos, target.getPosition());
                    }
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
                    value = linearNormalize(MIN_ASTEROID_MASS, MAX_ASTEROID_MASS, 0, 1, asteroid.getMass());
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
                    if (!TRAINING_GA && !TRAINING_TREE && gameIsEnding(space)) {
                        value += REALLY_BIG_NAV_WEIGHT; // We really want to go back to a base and deposit resources
                    }
                } else if (actionableObject.getId() == ship.getId()) {
                    continue; // Don't ever set the target to our current ship
                } else if (TRAINING_TREE && actionableObject instanceof Ship) {
                    value = SHOT_TRAINING_TARGET_WEIGHT;
                }
            } else if (object instanceof Beacon || object instanceof AiCore) {
                value = energyValue(ship);
            }

            Set<AbstractObject> obstructions = getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                if (!TRAINING_GA) {
                    value *= OBSTRUCTED_PATH_PENALTY; // We should be less likely to go towards objects with obstacles in the way
                } else {
                    value *= 1.2; // We are trying to have more instances of avoiding, to train our ship
                }
            }

            if (TRAINING_TREE) {
                // prioritize other ships so we can collect shot success data
                double opponentDistance = distanceToOtherShip(space, object);
                value *= linearNormalizeInverse(0, space.getWidth(), 0, 100, opponentDistance);
            }

            Position adjustedObjectPosition = interceptPosition(space, object.getPosition(), ship.getPosition());
            double rawDistance = space.findShortestDistance(ship.getPosition(), adjustedObjectPosition);
            // We want a small scaled distance (closer to zero) to be more important (dividing a smaller number increases value)
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
     * Calculates distance to the obstacle to attempt to shoot
     * @param space physics
     * @param object where we wanna shoot
     * @return
     */
    private double distanceToOtherShip(Toroidal2DPhysics space, AbstractObject object) {
        return space.getShips().stream()
                .filter(ship -> !getTeamName().equals(ship.getTeamName()))
                .mapToDouble(ship -> space.findShortestDistance(object.getPosition(), ship.getPosition()))
                .min()
                .orElse(Double.MAX_VALUE);
    }

    /**
     * Get a {@link MoveAction} that will take a ship through space from the current position to the target
     * <p>
     * This action attempts to move the ship to where the target will be in the future.
     * The ship and the target will collide even if the target is in motion at a constant rate.
     * This getTeamPurchases uses interceptPosition(Toroidal2DPhysics, Position, Position) to predict the
     * target's future location.
     * The ship will be going a speed of {@value TARGET_SHIP_SPEED} units by default when it reaches the target, depending on
     * the angle it needs to turn to reach nextStep (slower if angle is larger), so it doesn't overshoot each target
     *
     * @param space           physics
     * @param currentPosition The position of the ship at the starting time interval
     * @param target          The target object the action should aim for
     * @return An action to get the ship to the target's location
     */
    private MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, Position target) {
        Position adjustedTargetPosition = interceptPosition(space, target, currentPosition);
        Vector2D vectorToTarget = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition);
        double angleToTarget = vectorToTarget.getAngle();
        double angleToTurn = vectorToTarget.angleBetween(currentPosition.getTranslationalVelocity());
        double magnitude = linearNormalizeInverse(0.0, Math.PI, 2, TARGET_SHIP_SPEED, angleToTurn);

        Vector2D goalVelocity = Vector2D.fromAngle(angleToTarget, magnitude);
        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.RED_CIRCLE, target);
        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.RED_CIRCLE, adjustedTargetPosition);
        return new MoveAction(space, currentPosition, adjustedTargetPosition, goalVelocity);
    }

    /**
     * Used when training our decision tree (we gather data on shooting).
     * Heads towards the target ship, and slows down when it gets close to the ship (so we don't hit our target)
     * @param space physics
     * @param currentPosition where we are in space
     * @param target target ship we attempt to shoot
     * @return An action to collect data for our decision tree
     */
    private MoveAction getMoveActionToShoot(Toroidal2DPhysics space, Position currentPosition, Position target) {
        Vector2D vectorToTarget = space.findShortestDistanceVector(currentPosition, target);
        double angleToTarget = vectorToTarget.getAngle();
        double angleToTurn = vectorToTarget.angleBetween(currentPosition.getTranslationalVelocity());
        double magnitude = linearNormalizeInverse(0.0, Math.PI, 2, TARGET_SHIP_SPEED / 4, angleToTurn);
        if (vectorToTarget.getMagnitude() < 100) {
            magnitude = 0;
        }

        Vector2D goalVelocity = Vector2D.fromAngle(angleToTarget, magnitude);
        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.RED_CIRCLE, target);
        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.RED_CIRCLE, target);

        Vector2D vectorForShot = new Vector2D(target);
        vectorForShot = vectorForShot.add(Vector2D.fromAngle(angleToTarget, -shotDistance));
        Position positionForShot = new Position(vectorForShot);
        positionForShot.setOrientation(goalVelocity.getAngle());

        return new MoveActionWithOrientation(space, currentPosition, positionForShot, goalVelocity);
    }

    /**
     * If we're using GA, train our GA. Otherwise use our best chromosome from our knowledge file.
     * If this doesn't exist, fallback to our avoid action from project 2
     * @param space physics
     * @param obstacle which obstacle to avoid
     * @param target where we're heading
     * @param ship ship that's avoiding
     * @return an action to avoid
     */
    private AvoidAction avoidCrashAction(Toroidal2DPhysics space, AbstractObject obstacle, AbstractObject target, Ship ship) {
        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.YELLOW_CIRCLE, obstacle.getPosition());

        if (TRAINING_GA) {
            SessionCollection currentSession = knowledge.getSessionsFor(ship.getId());
            UUID obstacleUUID = obstacle.getId();
            ImmutableTeamInfo currentInfo = getTeamInfo(space, getTeamName());
            double score = currentInfo == null ? 0.0 : currentInfo.getScore();
            if (currentSession.shouldCreateNewSession(space, ship, obstacleUUID, score)) {
                AvoidSession newAvoidSession = new AvoidSession(space, ship, target, obstacle);
                currentSession.add(newAvoidSession);
                logger.fine(String.format("New session for: %s", obstacleUUID));
            }
            KnowledgeState state = KnowledgeState.build(space, ship, obstacle, target, graphicsUtil);
            return knowledge.getCurrentPolicy().getCurrentAction(space, ship,  state);
        } else {
            KnowledgeState state = KnowledgeState.build(space, ship, obstacle, target, graphicsUtil);
            KnowledgeChromosome bestChromosome = knowledge.bestPolicy();
            if (bestChromosome != null) {
                return bestChromosome.getCurrentAction(space, ship, state);
            } else {
                return oldAvoidCrashAction(space, obstacle, target, ship);
            }
        }
    }

    /**
     * Our avoid action from project 2 (actually works pretty well)
     * @param space physics
     * @param obstacle which obstacle to avoid
     * @param target where we're heading
     * @param ship ship that's avoiding
     * @return an action to avoid
     */
    private AvoidAction oldAvoidCrashAction(Toroidal2DPhysics space, AbstractObject obstacle, AbstractObject target, Ship ship) {
        Position currentPosition = ship.getPosition();
        Vector2D currentVector = new Vector2D(currentPosition);
        Vector2D obstacleVector = space.findShortestDistanceVector(currentPosition, obstacle.getPosition());
        Vector2D targetVector = space.findShortestDistanceVector(currentPosition, target.getPosition());
        double angleDifference = targetVector.angleBetween(obstacleVector);
        double newAngle;
        double collisionAvoidanceAngle = Math.PI / 2;
        if (angleDifference < 0) {
            newAngle = obstacleVector.getAngle() + collisionAvoidanceAngle;
        } else {
            newAngle = obstacleVector.getAngle() - collisionAvoidanceAngle;
        }
        int avoidanceMagnitude = obstacle.getRadius() + ship.getRadius();
        Vector2D avoidanceVector = Vector2D.fromAngle(newAngle, avoidanceMagnitude); // A smaller angle works much better
        Vector2D newTargetVector = currentVector.add(avoidanceVector);
        Position newTarget = new Position(newTargetVector);

        graphicsUtil.addGraphicPreset(GraphicsUtil.Preset.YELLOW_CIRCLE, obstacle.getPosition());
        Vector2D distanceVector = space.findShortestDistanceVector(currentPosition, newTarget);
        distanceVector = distanceVector.multiply(3);
        return new AvoidAction(space, currentPosition, newTarget, distanceVector, obstacle);
    }

    /**
     * Remove inconsistent objects from our space if died or if objects were removed
     *
     * @param space             physics
     * @param actionableObjects current actionable objects we are working with
     */
    @Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
        Set<UUID> targetsToRemove = new HashSet<>();

        for (Map.Entry<UUID, UUID> entry : currentTargets.entrySet()) {
            UUID shipId = entry.getKey();
            AbstractObject target = space.getObjectById(entry.getValue());
            Ship ship = (Ship) space.getObjectById(shipId);
            AbstractAction abstractAction = ship.getCurrentAction();
            boolean currentlyAvoiding = abstractAction instanceof AvoidAction;

            Position shipPosition = ship.getPosition();
            double distance = space.findShortestDistance(shipPosition, target.getPosition());
            int targetRadius = target.getRadius();
            boolean closeEnough = (target instanceof Base) && distance < targetRadius * 4 && ship.getResources().getTotal() == 0;
            SessionCollection currentSession;
            if(TRAINING_GA) {
                currentSession = knowledge.getSessionsFor(shipId);
            }

            // Handle when our target dies
            if (!target.isAlive() || space.getObjectById(target.getId()) == null || closeEnough) {
                if (TRAINING_GA && currentlyAvoiding && !closeEnough) {
                    AbstractObject obstacle = getObstacleForAvoidAction(abstractAction);
                    currentSession.invalidateLastSessionFor(obstacle.getId(), "target is gone");
                }
                targetsToRemove.add(shipId);
            }

            if (ship.isAlive() && TRAINING_GA) {
                // Mark avoid actions unsuccessful if we get too close to the obstacle
                if (currentlyAvoiding) {
                    AvoidAction action = (AvoidAction) abstractAction;
                    AbstractObject obstacle = getObstacleForAvoidAction(action);
                    Position obstaclePosition = obstacle.getPosition();
                    int totalRadius = ship.getRadius() + obstacle.getRadius();
                    if (space.findShortestDistance(shipPosition, obstaclePosition) < totalRadius) {
                        currentSession.markAvoidanceAsUnsuccessfulFor(obstacle.getId());
                    }
                    // Check if ship collides with an obstacle that is NOT our obstacle or target
                    for (AbstractObject object : space.getAllObjects()) {
                        if (!object.isAlive()) {
                            continue;
                        }

                        // skip them if they are the same object
                        if (ship.equals(object)) {
                            continue;
                        }

                        if (object.equals(obstacle)) {
                            continue;
                        }

                        double goingThe = space.findShortestDistance(ship.getPosition(), object.getPosition());

                        if (goingThe < (ship.getRadius() + object.getRadius())) {
                            currentSession.invalidateLastSessionFor(obstacle.getId(), "collided with unknown object");
                        }
                    }
                }
            } else {
                // Ship is ded :(
                targetsToRemove.add(ship.getId());
                if (TRAINING_GA && currentlyAvoiding) {
                    AbstractObject obstacle = getObstacleForAvoidAction(abstractAction);
                    currentSession.invalidateLastSessionFor(obstacle.getId(), "ship died");
                }
            }
        }

        for (UUID key : targetsToRemove) {
            currentTargets.remove(key);
        }
    }

    private AbstractObject getObstacleForAvoidAction(AbstractAction abstractAction) {
        if (abstractAction instanceof AvoidAction) {
            return ((AvoidAction) abstractAction).getObstacle();
        } else if (abstractAction instanceof AvoidActionRaw) {
            return ((AvoidActionRaw) abstractAction).getObstacle();
        } else {
            return null;
        }
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
     * (The further away we are, the closer to 1)
     * @param space       physics
     * @param rawDistance Input distance to convert
     * @return normalized distance, preserving ratio from 0 to 1
     */
    private double scaleDistance(Toroidal2DPhysics space, double rawDistance) {
        double maxDistance = maxDistance(space);
        return linearNormalizeInverse(0, maxDistance, 0, 1, maxDistance - rawDistance);
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
        Position adjustedTargetPosition = interceptPosition(space, targetPosition, currentPosition);
        Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition);
        double targetAngle = targetDirection.getAngle();
        double angleDiff = Math.abs(currentAngle - targetAngle);
        return linearNormalize(0, MAX_ANGLE, 0, 1, angleDiff);
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
        return linearNormalize(0, ship.getMaxEnergy(), 0, SHIP_ENERGY_VALUE_WEIGHT, missingEnergy);
    }

    /**
     * Determines the ship's cargoValue based on how many resources the ship has
     *
     * @param ship ship to fetch cargo value
     * @return Higher value if ship has a lot of resources
     */
    private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return linearNormalize(0, SHIP_MAX_RESOURCES, 0, SHIP_CARGO_VALUE_WEIGHT, total);
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
        return powerupUtil.getTeamPurchases(space, actionableObjects, resourcesAvailable, purchaseCosts);
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

    private void setDebugLevel(Level newLvl) {
        Logger rootLogger = Logger.getLogger("");
        Handler[] handlers = rootLogger.getHandlers();
        rootLogger.setLevel(newLvl);
        for (Handler h : handlers) {
            h.setLevel(newLvl);
        }

        Logger.getLogger("java.awt").setLevel(Level.OFF);
        Logger.getLogger("sun.awt").setLevel(Level.OFF);
        Logger.getLogger("sun.lwawt").setLevel(Level.OFF);
        Logger.getLogger("javax.swing").setLevel(Level.OFF);
    }

    // region Boilerplate
    @Override
    public void initialize(Toroidal2DPhysics space) {
        setDebugLevel(LOG_LEVEL);
        graphicsUtil = new GraphicsUtil(DEBUG_GRAPHIC);
        logger.addHandler(new ConsoleHandler());
        if (TRAINING_GA && !TRAINING_TREE) {
            powerupUtil = PowerupUtil.dummy(this, random);
        } else if (TRAINING_TREE) {
            powerupUtil = new TrainingPowerupUtil(this, random);
            shotDistance = random.nextInt(MAX_SHOOT_DISTANCE);
        } else {
            powerupUtil = new PowerupUtil(this, random);
        }
        knowledge = new KnowledgeUtil(getKnowledgeFile());
    }

    @Override
    public void shutDown(Toroidal2DPhysics space) {
        if (TRAINING_GA) {
            knowledge.think();
            knowledge.shutDown();
        }
        powerupUtil.shutDown();
    }

    @Override
    public Set<SpacewarGraphics> getGraphics() {
        return graphicsUtil.getGraphics();
    }
    // endregion
}
