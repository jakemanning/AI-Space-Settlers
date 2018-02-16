package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.util.*;

/**
 * A reflex agent for controlling a spacesettlers team client
 *
 * The ships move around the space according to their energy levels, nearby asteroids, ships, bases, and beacons.
 * @see #getMovementStart(Toroidal2DPhysics, Set)
 *
 * The ships shoot at enemy ships if they are within range and in front of the ship.
 * They also buy powerups whenever they have built up enough resources.
 * @see #getPowerups(Toroidal2DPhysics, Set)
 * @see #getTeamPurchases(Toroidal2DPhysics, Set, ResourcePile, PurchaseCosts)
 *
 * @author Bryan Capps and Jake Manning
 *
 */
public class BryanTeamClient extends TeamClient {
    static final boolean DEBUG = false;
    private static final double RANDOM_SHOOT_THRESHOLD = 0.35;
    private static final double COLLISION_AVOIDANCE_ANGLE = Math.PI / 2;
    private static final int BASE_RETURN_THRESHOLD = 2000;
    private static final double TARGET_SHIP_SPEED = 60;
    private static final int BASE_MIN_ENERGY_THRESHOLD = 1000;
    private static final double MAX_SHOT_ANGLE = Math.PI / 12;
    private static final int MAX_SHOT_DISTANCE = 100;
    private static final double SHIP_NEEDS_ENERGY_FACTOR = 0.2;
    private static final double GAME_IS_ENDING_FACTOR = 0.98;
    protected HashSet<SpacewarGraphics> graphics;

    // region Boilerplate
    @Override
    public void initialize(Toroidal2DPhysics space) {
        graphics = new HashSet<>();
    }

    @Override
    public void shutDown(Toroidal2DPhysics space) {
    }

    @Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
    }

    @Override
    public Set<SpacewarGraphics> getGraphics() {
        HashSet<SpacewarGraphics> newGraphics = new HashSet<>(graphics);
        graphics.clear();
        return newGraphics;
    }
    // endregion

    /**
     * Determine the next action for each ship (bases always do nothing).
     * <p>
     * If a ship needs energy then it will go towards the closest energy source (a beacon or one of our own bases).
     * If a ship has collected {@value BASE_RETURN_THRESHOLD} resources then it will go towards our nearest base.
     * If the game is nearly over then the ship will also go towards the nearest base.
     * Otherwise, the ship will go towards the nearest mineable asteroid or enemy ship or base.
     * Once a ship has a target, we determine if there is an obstacle in the way.
     * If so, then the ship attempts to avoid it using {@link #avoidCrashAction(Toroidal2DPhysics,
     * AbstractObject, AbstractObject, Ship)}.
     * Otherwise, the ship moves directly toward where we believe the ship will be located when the ship can reach
     * the target based on the target's current location and velocity.
     *
     * @param space             physics
     * @param actionableObjects the ships and bases for this team
     * @return Map of ship IDs to the actions they should take
     */
    @Override
    public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
                                                      Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, AbstractAction> actions = new HashMap<>();

        for (AbstractActionableObject actionable :  actionableObjects) {
            Position currentPosition = actionable.getPosition();

            if (actionable instanceof Ship) {
                // Figure out an action for this ship
                Ship ship = (Ship) actionable;
                Collection<AbstractObject> targets = new HashSet<>();
                if (shipNeedsEnergy(ship)) {
                    // head towards a base or beacon
                    targets.addAll(getEnergySources(space, getTeamName()));
                } else if (shipShouldDumpResources(ship) || gameIsEnding(space)) {
                    // head towards a base
                    targets.addAll(getTeamBases(space, getTeamName()));
                } else {
                    // head towards an asteroid to mine or an enemy ship or base to shoot
                    targets.addAll(getAsteroidsAndEnemies(space));
                }
                AbstractObject closestTarget = closest(space, currentPosition, targets);
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                AbstractObject obstruction = obstructionInPath(space, currentPosition,
                        closestTarget.getPosition(), obstructions, ship.getRadius());

                // Avoiding targets is an expensive action, only do so if have enough energy
                if (obstruction != null && !shipNeedsEnergy(ship)) {
                    AbstractAction action = avoidCrashAction(space, obstruction, closestTarget, ship);
                    actions.put(ship.getId(), action);
                    return actions;
                }
                MoveAction closeAction = getMoveAction(space, currentPosition, closestTarget);
                actions.put(ship.getId(), closeAction);
            } else if (actionable instanceof Base) {
                // Bases do nothing
                Base base = (Base) actionable;
                actions.put(base.getId(), new DoNothingAction());
            }
        }

        return actions;
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
            double distance = space.findShortestDistance(currentPosition, object.getPosition());
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

    // region Obstacle Avoidance

    /**
     * Get all the objects in the given space that the given ship considers obstructions.
     * This includes ships and bases from other teams, unmineable asteroids, and other ships on the team.
     * @param space physics
     * @param ship Ship to use as a basis for determining enemy ships and bases
     * @return The set of obstructions
     */
    Set<AbstractObject> getObstructions(Toroidal2DPhysics space, Ship ship) {
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
     * Check to see if following a straight line path between two given locations would result in a collision with a provided set of obstructions
     * Taken from {@link Toroidal2DPhysics#isPathClearOfObstructions(Position, Position, Set, int)}
     * with a few changes to return the nearest obstruction to the startPosition
     *
     * @param startPosition the starting location of the straight line path
     * @param goalPosition  the ending location of the straight line path
     * @param obstructions  an Set of AbstractObject obstructions (i.e., if you don't wish to consider mineable asteroids or beacons obstructions)
     * @param freeRadius    used to determine free space buffer size
     * @return The closest obstacle between a start and goal position, if one exists
     * @author Andrew and Thibault
     */
    AbstractObject obstructionInPath(Toroidal2DPhysics space, Position startPosition, Position goalPosition,
                                     Set<AbstractObject> obstructions, int freeRadius) {
        // Shortest straight line path from startPosition to goalPosition
        Vector2D pathToGoal = space.findShortestDistanceVector(startPosition, goalPosition);
        // Distance of straight line path
        double distanceToGoal = pathToGoal.getMagnitude();

        AbstractObject closestObstacle = null; // Closest obstacle in the path
        double distanceToObstacle = Double.MAX_VALUE;

        // Calculate distance between obstruction center and path (including buffer for ship movement)
        // Uses hypotenuse * sin(theta) = opposite (on a right hand triangle)
        Vector2D pathToObstruction; // Vector from start position to obstruction
        double angleBetween;        // Angle between vector from start position to obstruction

        // Loop through obstructions
        for (AbstractObject obstruction : obstructions) {
            // If the distance to the obstruction is greater than the distance to the end goal, ignore the obstruction
            Position interceptPosition = interceptPosition(space, obstruction.getPosition(), startPosition);
            pathToObstruction = space.findShortestDistanceVector(startPosition, interceptPosition);
            if (pathToObstruction.getMagnitude() > distanceToGoal) {
                continue;
            }

            // Ignore angles > 90 degrees
            angleBetween = Math.abs(pathToObstruction.angleBetween(pathToGoal));
            if (angleBetween > Math.PI / 2) {
                continue;
            }

            // Compare distance between obstruction and path with buffer distance
            if (pathToObstruction.getMagnitude() * Math.sin(angleBetween)
                    < obstruction.getRadius() + freeRadius * 1.5) {
                double distance = space.findShortestDistance(startPosition, obstruction.getPosition());
                if (distance < distanceToObstacle) {
                    distanceToObstacle = distance;
                    closestObstacle = obstruction;
                }
            }
        }

        return closestObstacle;
    }

    /**
     * Create an action for avoiding an obstacle
     *
     * It finds the shortest path around the obstacle (either left or right) by comparing the angles
     * for the paths from the ship to the target and from the ship to the obstacle.
     * It then sets a location that is the length of the ship's radius away from the obstacle in that direction
     * and returns an action to go through that location and past the obstacle.
     *
     * @param space physics
     * @param obstacle The obstacle to avoid
     * @param target The target object the ship is trying to get to
     * @param ship The ship that is trying to get around the obstacle and to the target
     * @return A subclass of {@link MoveAction} for avoiding the obstacle
     */
    AvoidAction avoidCrashAction(Toroidal2DPhysics space, AbstractObject obstacle, AbstractObject target, Ship ship) {
        Position currentPosition = ship.getPosition();
        // convert the currentPosition to a vector to do manipulations with
        Vector2D currentVector = new Vector2D(currentPosition);
        Vector2D obstacleVector = space.findShortestDistanceVector(currentPosition, obstacle.getPosition());
        Vector2D targetVector = space.findShortestDistanceVector(currentPosition, target.getPosition());
        // difference between the angle to the obstacle and the angle to the target
        double angleDifference = targetVector.angleBetween(obstacleVector);
        double newAngle;
        // figure out which way to turn around the obstacle
        if (angleDifference < 0) {
            newAngle = obstacleVector.getAngle() + COLLISION_AVOIDANCE_ANGLE;
        } else {
            newAngle = obstacleVector.getAngle() - COLLISION_AVOIDANCE_ANGLE;
        }
        // aim for a spot that is just outside the obstacle's radius
        int avoidanceMagnitude = obstacle.getRadius() + ship.getRadius();
        Vector2D avoidanceVector = Vector2D.fromAngle(newAngle, avoidanceMagnitude); // A smaller angle works much better
        Vector2D newTargetVector = currentVector.add(avoidanceVector);
        Position newTarget = new Position(newTargetVector);

        if(DEBUG) {
            graphics.add(new CircleGraphics(2, Color.YELLOW, obstacle.getPosition()));
        }
        Vector2D speedVector = space.findShortestDistanceVector(currentPosition, newTarget);
        // speed up when avoiding to alleviate some bumps from the obstacle moving mid avoidance
        speedVector = speedVector.multiply(3);
        return new AvoidAction(space, currentPosition, newTarget, speedVector);
    }
    // endregion

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
    MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, AbstractObject target) {
        Position targetPosition = target.getPosition();
        Position adjustedTargetPosition = interceptPosition(space, targetPosition, currentPosition);

        // aim to be going the target speed and at the most direct angle
        double goalAngle = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition).getAngle();
        Vector2D goalVelocity = Vector2D.fromAngle(goalAngle, TARGET_SHIP_SPEED);
        if(DEBUG) {
            graphics.add(new CircleGraphics(2, Color.RED, adjustedTargetPosition));
            graphics.add(new CircleGraphics(2, Color.RED, targetPosition));
        }
        return new MoveAction(space, currentPosition, adjustedTargetPosition, goalVelocity);
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
    private Position interceptPosition(Toroidal2DPhysics space, Position targetPosition, Position shipLocation) {
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
     * Whether the ship needs more energy or not.
     * Compares the ship's energy level as a percentage of its max energy level with {@value SHIP_NEEDS_ENERGY_FACTOR}
     * @param ship The ship that may need more energy
     * @return True if the ship needs more energy, false otherwise
     */
    protected boolean shipNeedsEnergy(Ship ship) {
        return ship.getEnergy() < ship.getMaxEnergy() * SHIP_NEEDS_ENERGY_FACTOR;
    }

    // region Objects in Space

    /**
     * Get all the available sources of energy in the space
     * @param space physics
     * @param teamName The name of the team whose bases are available sources of energy
     * @return A set of objects that can be used as sources of energy
     */
    private Set<AbstractObject> getEnergySources(Toroidal2DPhysics space, String teamName) {
        Set<AbstractObject> energySources = new HashSet<>();
        energySources.addAll(space.getBeacons());
        Set<Base> ourBases = new HashSet<>();
        for (Base base : space.getBases()) {
            if (base.getTeamName().equals(teamName) && base.getHealingEnergy() > BASE_MIN_ENERGY_THRESHOLD) {
                ourBases.add(base);
            }
        }
        energySources.addAll(ourBases);
        return energySources;
    }

    /**
     * Whether the ship should go to a base to dump its resources or not
     * @param ship The ship that should possibly return to a base with its resources
     * @return True if the ship has collected enough resources to return to the base, false otherwise
     */
    private boolean shipShouldDumpResources(Ship ship) {
        return ship.getResources().getTotal() > BASE_RETURN_THRESHOLD;
    }

    /**
     * Whether the game is ending soon or not. Ships should return any resources before the game is over.
     * @param space The Toroidal2DPhysics for the game
     * @return True if the game is nearly over, false otherwise
     */
    boolean gameIsEnding(Toroidal2DPhysics space) {
        return space.getCurrentTimestep() > space.getMaxTime() * GAME_IS_ENDING_FACTOR;
    }

    /**
     * Gets all the mineable asteroids and other teams' bases and ships
     *
     * @param space physics
     * @return A set of all the mineable asteroids and enemy targets in the space
     */
    private Set<AbstractObject> getAsteroidsAndEnemies(Toroidal2DPhysics space) {
        Set<AbstractObject> targets = new HashSet<>();
        targets.addAll(getEnemyTargets(space, getTeamName()));
        Set<Asteroid> asteroids = getMineableAsteroids(space);
        targets.addAll(asteroids);
        return targets;
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

    /**
     * Get all the ships and bases that belong to other teams
     * @param space physics
     * @param teamName The name of the team whose ships and bases are not enemy targets
     * @return
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
     * Find the maximum distance between two objects in the given space
     *
     * @param space physics
     * @return The maximum distance in the space
     */
    double maxDistance(Toroidal2DPhysics space) {
        // Since the space wraps around, the furthest distance is from the center to a corner
        return Math.sqrt(Math.pow(space.getHeight(), 2) + Math.pow(space.getWidth(), 2)) / 2;
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

        return purchases;
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
                // shoot if we're in position and do not need energy
                if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) {
                    shoot(powerupMap, ship);
                } else if(ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                    // equip the double max energy powerup
                    powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                }
            }
        }
        return powerupMap;
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
}
