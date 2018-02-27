package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.AbstractWeapon;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

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
public class JakeTeamClient extends TeamClient {
    private static final boolean DEBUG = true;
    private static final double RANDOM_SHOOT_THRESHOLD = 0.35;
	private static final double OBSTRUCTED_PATH_PENALTY = 0.5;
    private static final int SHIP_MAX_RESOURCES = 5000;
    private static final int MAX_ASTEROID_MASS = 2318;
    private static final int MIN_ASTEROID_MASS = 2000;
    private static final double TARGET_SHIP_SPEED = 60;
    private static final int SHIP_ENERGY_VALUE_WEIGHT = 6;
    private static final int SHIP_CARGO_VALUE_WEIGHT = 6;
    private static final double MAX_ANGLE = Math.PI / 2;
    private static final int REALLY_BIG_NAV_WEIGHT = 100;
    private static final double SHIP_NEEDS_ENERGY_FACTOR = 0.2;
    private static final int NEIGHBORHOOD_RADIUS = 100;
    private static final int AVOID_RADIUS = 3;
    private static final double MAX_SHOT_ANGLE = Math.PI / 12;
    private static final double GAME_IS_ENDING_FACTOR = 0.98;
    private static final int MAX_SHOT_DISTANCE = 100;
    protected Set<SpacewarGraphics> graphics;
    private Map<UUID, Plan> plans = new HashMap<>();
    private Set<UUID> shieldedShips = new HashSet<>();

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
            AStar currentPlan = (AStar) plans.get(actionable.getId());

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Set<AbstractObject> allObjects = space.getAllObjects();

                // TODO: Eventually make bases be able to shield
                if(shouldShield(space, ship, allObjects)) {
                    shieldedShips.add(ship.getId());
                } else if(ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) {
                    shieldedShips.remove(ship.getId());
                }

                if (currentPlan == null || currentPlan.isDone()) {
                    AbstractObject nextGoalObject = bestValue(space, ship, space.getAllObjects());
                    currentPlan = AStar.forObject(nextGoalObject, ship, space);
                    plans.put(ship.getId(), currentPlan);
                }

                Position currentStep = currentPlan.getStep();
                int closeEnough = ship.getRadius() * 2;
                if (currentStep != null && space.findShortestDistance(shipPos, currentStep) > closeEnough) {
                    graphics.addAll(currentPlan.getGraphics());
                    actions.put(ship.getId(), getMoveAction(space, shipPos, currentStep));
                    continue;
                }
                MoveAction action = getMoveAction(space, shipPos, currentStep);
                currentPlan.completeStep();
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
                    value = linearNormalize(MIN_ASTEROID_MASS, MAX_ASTEROID_MASS, 1, asteroid.getMass());
                }
            } else if (object instanceof AbstractActionableObject) {
                AbstractActionableObject actionableObject = (AbstractActionableObject) object;
                if (isOurBase(actionableObject)) {
                    value = energyValue(ship) + cargoValue(ship);
                    if (gameIsEnding(space)) {
                        value += REALLY_BIG_NAV_WEIGHT; // We really want to go back to a base and deposit resources
                    }
                } else if (actionableObject.getId() == ship.getId()) {
                    continue; // Don't ever set the target to our current ship
                }
            } else if (object instanceof Beacon) {
                value = energyValue(ship);
            }

            Set<AbstractObject> obstructions = getObstructions(space, ship);
            if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
                value *= OBSTRUCTED_PATH_PENALTY; // We should be less likely to go towards objects with obstacles in the way
            }

            Position adjustedObjectPosition = interceptPosition(space, object.getPosition(), ship.getPosition());
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
     *
     * This action attempts to move the ship to where the target will be in the future.
     * The ship and the target will collide even if the target is in motion at a constant rate.
     * This method uses {@link #interceptPosition(Toroidal2DPhysics, Position, Position)} to predict the
     * target's future location.
     * The ship will be going a speed of {@value TARGET_SHIP_SPEED} units when it reaches the target.
     *
     * @param space physics
     * @param currentPosition The position of the ship at the starting time interval
     * @param target The target object the action should aim for
     * @return An action to get the ship to the target's location
     */
    private MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, Position target) {
        Position adjustedTargetPosition = interceptPosition(space, target, currentPosition);

        // aim to be going the target speed and at the most direct angle
        double goalAngle = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition).getAngle();
        Vector2D goalVelocity = Vector2D.fromAngle(goalAngle, TARGET_SHIP_SPEED);
        return new MoveAction(space, currentPosition, adjustedTargetPosition, goalVelocity);
    }

	/**
	 * Remove inconsistent objects from our space if died or if objects were removed
	 *
	 * @param space physics
	 * @param actionableObjects current actionable objects we are working with
	 */
	@Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
	    Map<UUID, UUID> targets = new HashMap<>();

        for (Map.Entry<UUID, Plan> entry : plans.entrySet()) {
            UUID shipId = entry.getKey();
            Plan plan = entry.getValue();
            AbstractObject goal = plan.getGoal();
            AbstractObject ship = space.getObjectById(shipId);

            double distance = space.findShortestDistance(ship.getPosition(), goal.getPosition());
            int targetRadius = goal.getRadius();
            boolean closeEnough = distance < targetRadius * 3;
            if (!goal.isAlive() || space.getObjectById(goal.getId()) == null || closeEnough) {
                targets.put(shipId, goal.getId());
            }
        }

        for(UUID key : targets.keySet()) {
            plans.remove(key);
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
     * @param oldMax Original scale end
     * @param newMax New linear scale end
     * @param input  What we want to convert
     * @return Linearly scaled integer from old range to new range
     */
    private static double linearNormalize(double oldMin, double oldMax, double newMax, double input) {
        if (input < oldMin) {
            input = oldMin;
        } else if (input > oldMax) {
            input = oldMax;
        }

        double oldRange = oldMax - oldMin;
        if (oldRange == 0) {
            return (double) 0;
        } else {
            double newRange = newMax - (double) 0;
            return (((input - oldMin) * newRange) / oldRange) + (double) 0;
        }
    }

    /**
     * Figure out where the moving target and the ship will meet if the target maintains its current velocity
     * and the ship moves roughly at {@value TARGET_SHIP_SPEED}
     * https://stackoverflow.com/questions/2248876/2d-game-fire-at-a-moving-target-by-predicting-intersection-of-projectile-and-u
     *
     * @param space The Toroidal2DPhysics for the game
     * @param targetPosition Position of the target at this instant
     * @param shipLocation Position of the ship at this instant
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
     * Determines whether a given actionableObject is an enemy weapon
     *
     * @param weapon object to check
     * @return Whether the object is an enemy weapon
     */
    private boolean isEnemyWeapon(AbstractWeapon weapon) {
        return !weapon.getFiringShip().getTeamName().equals(getTeamName());
    }

    /**
     * Whether the game is ending soon or not. Ships should return any resources before the game is over.
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
     * @param space physics
     * @param rawDistance Input distance to convert
     * @return normalized distance, preserving ratio from 0 to 1
     */
    private double scaleDistance(Toroidal2DPhysics space, double rawDistance) {
        double maxDistance = maxDistance(space);
        double scaledDistance = linearNormalize(0, maxDistance, 1, maxDistance - rawDistance);
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
        return linearNormalize(0, MAX_ANGLE, 1, angleDiff);
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
        return linearNormalize(0, ship.getMaxEnergy(), SHIP_ENERGY_VALUE_WEIGHT, missingEnergy);
    }

    /**
     * Determines the ship's cargoValue based on how many resources the ship has
     *
     * @param ship ship to fetch cargo value
     * @return Higher value if ship has a lot of resources
     */
    private double cargoValue(Ship ship) {
        double total = ship.getResources().getTotal();
        return linearNormalize(0, SHIP_MAX_RESOURCES, SHIP_CARGO_VALUE_WEIGHT, total);
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

    // endregion

    // region Powerups and Purchases
    /**
     * Get the team's purchases for this turn
     * Buys bases and tries to spread them out in the space. Also buys double max energy powerups.
     * It buys whenever it can afford to.
     *
     * @param space physics
     * @param actionableObjects Ships and bases on the team
     * @param resourcesAvailable how much resourcesAvailable you have
     * @param purchaseCosts Used to see if we can afford to buy things
     * @return A map of ship or base IDs to types of purchases we want to buy
     */
    @Override
    public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                     Set<AbstractActionableObject> actionableObjects,
                                                     ResourcePile resourcesAvailable,
                                                     PurchaseCosts purchaseCosts) {

        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        long baseCount = actionableObjects.stream()
                .filter(o -> o instanceof Base)
                .count();
        // leave some wiggle room about how far away the ship needs to be to buy a base
        final double baseDistanceFactor = 0.8;
        final double baseBuyingDistance = baseDistanceFactor * maxDistance(space) / baseCount;

        if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
            for (AbstractActionableObject actionableObject : actionableObjects) {
                if (actionableObject instanceof Ship) {
                    Ship ship = (Ship) actionableObject;
                    Set<Base> bases = getTeamBases(space, getTeamName());

                    // how far away is this ship to a base of my team?
                    double minDistance = Double.MAX_VALUE;
                    for (Base base : bases) {
                        double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
                        if (distance < minDistance) {
                            minDistance = distance;
                        }
                    }

                    if (minDistance > baseBuyingDistance) {
                        purchases.put(ship.getId(), PurchaseTypes.BASE);
                        break;
                    }
                }
            }
        }

        // buy double max energy powerups if we can afford to
        if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY, resourcesAvailable)) {
            actionableObjects.stream()
                    .filter(actionableObject -> actionableObject instanceof Ship)
                    .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                    .ifPresent(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
        }

        // buy shield powerup if we can afford to
        if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_SHIELD, resourcesAvailable)) {
            actionableObjects.stream()
                    .filter(actionableObject -> actionableObject instanceof Ship)
                    .filter(ship -> !ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD))
                    .forEach(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_SHIELD));
        }

        return purchases;
    }

    /**
     * Find the maximum distance between two objects in the given space
     *
     * @param space physics
     * @return The maximum distance in the space
     */
    private double maxDistance(Toroidal2DPhysics space) {
        // Since the space wraps around, the furthest distance is from the center to a corner
        return Math.sqrt(Math.pow(space.getHeight(), 2) + Math.pow(space.getWidth(), 2)) / 2;
    }

    /**
     * Shoot at other ships and use powerups.
     *
     * We shoot at any enemy ships or bases if we are in
     * position according to {@link #inPositionToShoot(Toroidal2DPhysics, Position, AbstractObject)}.
     *
     * @param space physics
     * @param actionableObjects the ships and bases for this team
     * @return A map from ship IDs to powerup types
     */
    public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                           Set<AbstractActionableObject> actionableObjects) {

        HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

        for (AbstractObject actionable :  actionableObjects) {
            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Set<AbstractActionableObject> enemyShips = getEnemyTargets(space, getTeamName());
                AbstractObject closestEnemyShip = closest(space, ship.getPosition(), enemyShips);
                if(ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) { // protect ship if we're in position and do not need energy
                    if(shieldedShips.contains(ship.getId()) != ship.isShielded()) { // Only if the status of the ship has changed
                        powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.TOGGLE_SHIELD);
                    }
                } else if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) { // shoot if we're in position and do not need energy
                    shoot(powerupMap, ship);
                } else if(ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                    // equip the double max energy powerup
                    powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                }
            }
        }
        return powerupMap;
    }

    private boolean shouldShield(Toroidal2DPhysics space, Ship ship, Set<AbstractObject> objects) {
        if(!ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) {
            return false;
        }

        boolean weaponIsClose = false;
        for(AbstractObject object : objects) {
            if (!(object instanceof AbstractWeapon)) {
                continue;
            }

            AbstractWeapon weapon = (AbstractWeapon) object;
            double weaponDistance = space.findShortestDistance(ship.getPosition(), weapon.getPosition());
            if (isEnemyWeapon(weapon) && weaponDistance < ship.getRadius() * AVOID_RADIUS) {
                weaponIsClose = true;
                break;
            }
        }
        return weaponIsClose;
    }

    /**
     * Whether the ship needs more energy or not.
     * Compares the ship's energy level as a percentage of its max energy level with {@value SHIP_NEEDS_ENERGY_FACTOR}
     * @param ship The ship that may need more energy
     * @return True if the ship needs more energy, false otherwise
     */
    private boolean shipNeedsEnergy(Ship ship) {
        return ship.getEnergy() < ship.getMaxEnergy() * SHIP_NEEDS_ENERGY_FACTOR;
    }

    /**
     * Determine if the ship at currentPosition is in position to shoot the target
     *
     * If the target is within a distance of {@value MAX_SHOT_DISTANCE} and at an
     * angle less than {@value MAX_SHOT_ANGLE} from the ship's current orientation then
     * the ship is considered in position to shoot the target.
     *
     * @param space physics
     * @param currentPosition The current position of a ship
     * @param target The potential target for the ship to shoot
     * @return True if the target can be shot from the currentPosition, false otherwise
     */
    private boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition,
                                      AbstractObject target) {
        Position targetPosition = target.getPosition();
        boolean close = space.findShortestDistance(currentPosition, targetPosition) < MAX_SHOT_DISTANCE;
        if (!close) {
            return false;
        }
        Vector2D targetVector = space.findShortestDistanceVector(currentPosition, targetPosition);
        double targetAngle = targetVector.getAngle();
        double currentAngle = currentPosition.getOrientation();
        double angleDifference = Math.abs(targetAngle - currentAngle);
        return angleDifference < MAX_SHOT_ANGLE;
    }

    /**
     * Fire a missile with a probability of {@value RANDOM_SHOOT_THRESHOLD}
     * Avoid shooting too frequently by shooting with a set probability
     * @param powerupMap A map from ship IDs to powerup types that is added to when shooting
     * @param ship The ship that will shoot
     */
    private void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
        if (random.nextDouble() < RANDOM_SHOOT_THRESHOLD) {
            powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
        }
    }
    // endregion

    // region Getting Objects
    /**
     * Get all the objects in the given space that the given ship considers obstructions.
     * This includes ships and bases from other teams, unmineable asteroids, and other ships on the team.
     * @param space physics
     * @param ship Ship to use as a basis for determining enemy ships and bases
     * @return The set of obstructions
     */
    private Set<AbstractObject> getObstructions(Toroidal2DPhysics space, Ship ship) {
        Set<AbstractActionableObject> enemies = getEnemyTargets(space, getTeamName());
        Set<Asteroid> asteroids = getUnmineableAsteroids(space);
        Set<Ship> friendlyShips = getFriendlyShips(space, ship);
        Set<AbstractObject> obstacles = new HashSet<>();
        obstacles.addAll(enemies);
        obstacles.addAll(asteroids);
        obstacles.addAll(friendlyShips);
        return obstacles;
    }

    /**
     * Get all the unmineable asteroids in the space
     *
     * @param space physics
     * @return A set of all the unmineable asteroids
     */
    private Set<Asteroid> getUnmineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> asteroids = new HashSet<>(space.getAsteroids());
        asteroids.removeAll(getMineableAsteroids(space));
        return asteroids;
    }

    /**
     * Get all the mineable asteroids in the space
     * @param space physics
     * @return A set of all the mineable asteroids
     */
    private Set<Asteroid> getMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> results = new HashSet<>();
        for (Asteroid asteroid : space.getAsteroids()) {
            if (asteroid.isMineable()) {
                results.add(asteroid);
            }
        }
        return results;
    }

    /**
     * Get all the ships and bases that belong to other teams
     * @param space physics
     * @param teamName The name of the team whose ships and bases are not enemy targets
     * @return all enemies
     */
    private Set<AbstractActionableObject> getEnemyTargets(Toroidal2DPhysics space, String teamName) {
        Set<AbstractActionableObject> enemies = new HashSet<>();
        // get enemy ships
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getTeamName(), teamName)) {
                enemies.add(ship);
            }
        }
        // get enemy bases
        for (Base base : space.getBases()) {
            if (!Objects.equals(base.getTeamName(), teamName)) {
                enemies.add(base);
            }
        }
        return enemies;
    }

    /**
     * Get all the ships that are on the same team as the given ship (minus the given ship)
     * @param space physics
     * @param ship The ship to use to get the ships on the same team
     * @return A set of all the ships on the same team as the given ship
     */
    private Set<Ship> getFriendlyShips(Toroidal2DPhysics space, Ship ship) {
        Set<Ship> results = new HashSet<>();
        for (Ship otherShip : space.getShips()) {
            // check that the team names match, but the ship IDs do not
            if (otherShip.getTeamName().equals(ship.getTeamName()) && !otherShip.getId().equals(ship.getId())) {
                results.add(otherShip);
            }
        }
        return results;
    }

    /**
     * Find the closest of a collection of objects to a given position
     * @param space physics
     * @param currentPosition The position from which to base the distance to the objects
     * @param objects The collection of objects to measure
     * @param <T> The type of objects
     * @return The object in objects that is closest to currentPosition
     */
    private <T extends AbstractObject> T closest(Toroidal2DPhysics space, Position currentPosition,
                                                 Collection<T> objects) {
        T closest = null;
        double minimum = Double.MAX_VALUE;
        for (T object : objects) {
            Position interceptPosition = interceptPosition(space, object.getPosition(), currentPosition);
            double distance = space.findShortestDistance(currentPosition, interceptPosition);
            if (object instanceof Asteroid) {
                // More heavily weigh asteroids with more resources
                Asteroid asteroid = (Asteroid) object;
                distance = distance / asteroid.getResources().getTotal();
            }
            if (distance < minimum) {
                minimum = distance;
                closest = object;
            }
        }
        return closest;
    }

    /**
     * Get all the bases that belong to the team with the given team name
     * @param space physics
     * @param teamName The team name for the bases
     * @return A set of bases that belong to the team with the given team name
     */
    private Set<Base> getTeamBases(Toroidal2DPhysics space, String teamName) {
        Set<Base> results = new HashSet<>();
        for (Base base : space.getBases()) {
            if (base.getTeamName().equals(teamName)) {
                results.add(base);
            }
        }
        return results;
    }
    // endregion

    // region Boilerplate
    @Override
    public void initialize(Toroidal2DPhysics space) {
        graphics = new HashSet<>();
    }

    @Override
    public void shutDown(Toroidal2DPhysics space) {
    }

    @Override
    public Set<SpacewarGraphics> getGraphics() {
        HashSet<SpacewarGraphics> newGraphics = new HashSet<>(graphics);
        graphics.clear();
        if(DEBUG) {
            return newGraphics;
        } else {
            return new HashSet<>();
        }
    }
    // endregion
}
