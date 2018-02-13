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
 * A team of alright agents
 *
 * The agents move towards the closest enemy ship and shoots when it gets close.
 * If it's low on energy then it goes to the nearest beacon or friendly base.
 * Kinda avoids obstacles, but not very well.
 * @author Bryan Capps
 *
 */
public class BryanTeamClient extends TeamClient {
    private static final double RANDOM_SHOOT_THRESHOLD = 0.35;
    private static final double COLLISION_AVOIDANCE_ANGLE = Math.PI / 2;
    private static final int BASE_RETURN_THRESHOLD = 2000;
    protected static final double TARGET_SHIP_SPEED = 25;
    private static final int BASE_MIN_ENERGY_THRESHOLD = 1000;
    private static final double MAX_SHOT_ANGLE = Math.PI / 12;
    private static final int MAX_SHOT_DISTANCE = 100;
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

    @Override
    public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
                                                      Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, AbstractAction> actions = new HashMap<>();

        for (AbstractActionableObject actionable :  actionableObjects) {
            Position currentPosition = actionable.getPosition();

            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Collection<AbstractObject> targets = new HashSet<>();
                if (shipNeedsEnergy(ship)) {
                    targets.addAll(getEnergySources(space, getTeamName()));
                } else if (shipShouldDumpResources(ship) || gameIsEnding(space)) {
                    targets.addAll(getTeamBases(space, getTeamName()));
                } else {
                    targets.addAll(getAsteroidsAndEnemies(space));
                }
                AbstractObject closestTarget = closest(space, currentPosition, targets);
                Set<AbstractObject> obstructions = getObstructions(space, ship);
                AbstractObject obstruction = obstructionInPath(space, currentPosition, closestTarget.getPosition(),  obstructions, ship.getRadius());

                // Avoiding targets is an expensive action, only do so if have enough energy
                if (obstruction != null && !shipNeedsEnergy(ship)) {
                    AbstractAction action = avoidCrashAction(space, obstruction, closestTarget, ship);
                    actions.put(ship.getId(), action);
                    return actions;
                }
                MoveAction closeAction = getMoveAction(space, currentPosition, closestTarget);
                actions.put(ship.getId(), closeAction);
            } else if (actionable instanceof Base) {
                Base base = (Base) actionable;
                actions.put(base.getId(), new DoNothingAction());
            }
        }

        return actions;
    }

    private <T extends AbstractObject> T closest(Toroidal2DPhysics space, Position currentPosition,
                                                 Collection<T> objects) {
        T closest = null;
        double minimum = Double.MAX_VALUE;
        for (T object : objects) {
            double distance = space.findShortestDistance(currentPosition, object.getPosition());
            if (object instanceof Asteroid) {
                Asteroid asteroid = (Asteroid) object;
                distance = distance / asteroid.getMass();
            }
            if (distance < minimum) {
                minimum = distance;
                closest = object;
            }
        }
        return closest;
    }

    // region Obstacle Avoidance
    Set<AbstractObject> getObstructions(Toroidal2DPhysics space, Ship ship) {
        Set<AbstractActionableObject> enemies = getEnemyTargets(space, getTeamName());
        Set<Asteroid> asteroids = getNonMineableAsteroids(space);
        Set<Ship> friendlyShips = getFriendlyShips(space, ship);
        Set<AbstractObject> obstacles = new HashSet<>();
        obstacles.addAll(enemies);
        obstacles.addAll(asteroids);
        obstacles.addAll(friendlyShips);
        return obstacles;
    }

    /**
     * Check to see if following a straight line path between two given locations would result in a collision with a provided set of obstructions
     *
     * @param startPosition the starting location of the straight line path
     * @param goalPosition  the ending location of the straight line path
     * @param obstructions  an Set of AbstractObject obstructions (i.e., if you don't wish to consider mineable asteroids or beacons obstructions)
     * @param freeRadius    used to determine free space buffer size
     * @return The closest obstacle between a start and goal position, if exists
     * @author Andrew and Thibault
     */
    AbstractObject obstructionInPath(Toroidal2DPhysics space, Position startPosition, Position goalPosition, Set<AbstractObject> obstructions, int freeRadius) {
        Vector2D pathToGoal = space.findShortestDistanceVector(startPosition, goalPosition);    // Shortest straight line path from startPosition to goalPosition
        double distanceToGoal = pathToGoal.getMagnitude();                                        // Distance of straight line path

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
            if (pathToObstruction.getMagnitude() * Math.sin(angleBetween) < obstruction.getRadius() + freeRadius * 1.5) {
                double distance = space.findShortestDistance(startPosition, obstruction.getPosition());
                if (distance < distanceToObstacle) {
                    distanceToObstacle = distance;
                    closestObstacle = obstruction;
                }
            }
        }

        return closestObstacle;
    }

    AvoidAction avoidCrashAction(Toroidal2DPhysics space, AbstractObject obstacle, AbstractObject target, Ship ship) {
        Position currentPosition = ship.getPosition();
        Vector2D currentVector = new Vector2D(currentPosition);
        Vector2D obstacleVector = space.findShortestDistanceVector(currentPosition, obstacle.getPosition());
        Vector2D targetVector = space.findShortestDistanceVector(currentPosition, target.getPosition());
        double angleDifference = targetVector.angleBetween(obstacleVector);
        double newAngle;
        if (angleDifference < 0) {
            newAngle = obstacleVector.getAngle() + COLLISION_AVOIDANCE_ANGLE;
        } else {
            newAngle = obstacleVector.getAngle() - COLLISION_AVOIDANCE_ANGLE;
        }
        int avoidanceMagnitude = obstacle.getRadius() + ship.getRadius();
        Vector2D avoidanceVector = Vector2D.fromAngle(newAngle, avoidanceMagnitude); // A smaller angle works much better
        Vector2D newTargetVector = currentVector.add(avoidanceVector);
        Position newTarget = new Position(newTargetVector);

        graphics.add(new CircleGraphics(2, Color.YELLOW, obstacle.getPosition()));
        Vector2D distanceVector = space.findShortestDistanceVector(currentPosition, newTarget);
        distanceVector = distanceVector.multiply(3);
        return new AvoidAction(space, currentPosition, newTarget, distanceVector);
    }
    // endregion

    MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, AbstractObject target) {
        Position targetPosition = target.getPosition();
        Position adjustedTargetPosition = interceptPosition(space, targetPosition, currentPosition);
        double goalAngle = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition).getAngle();
        Vector2D goalVelocity = Vector2D.fromAngle(goalAngle, TARGET_SHIP_SPEED);
        graphics.add(new CircleGraphics(2, Color.RED, adjustedTargetPosition));
        graphics.add(new CircleGraphics(2, Color.RED, targetPosition));
        return new MoveAction(space, currentPosition, adjustedTargetPosition, goalVelocity);
    }

    /**
     * Figure out where the moving target and the cannon will meet when the cannon is fired in that direction
     * https://stackoverflow.com/questions/2248876/2d-game-fire-at-a-moving-target-by-predicting-intersection-of-projectile-and-u
     *
     * @param space The Toroidal2DPhysics for the game
     * @param targetPosition Position of the target at this instant
     * @param cannonPosition Position of the cannon at this instant
     * @return Position to aim the cannon in order to collide with the target
     */
    private Position interceptPosition(Toroidal2DPhysics space, Position targetPosition, Position cannonPosition) {
        double targetVelX = targetPosition.getTranslationalVelocityX();
        if (Math.abs(targetVelX) < 1) {
            targetVelX = 0;
        }
        double targetVelY = targetPosition.getTranslationalVelocityY();
        if (Math.abs(targetVelY) < 1) {
            targetVelY = 0;
        }
        double targetX = targetPosition.getX();
        double targetY = targetPosition.getY();
        double cannonX = cannonPosition.getX();
        double cannonY = cannonPosition.getY();

        double negativeTargetX = targetX - space.getWidth();
        if (Math.abs(negativeTargetX - cannonX) < Math.abs(targetX - cannonX)) {
            targetX = negativeTargetX;
        }
        double extraTargetX = targetX + space.getWidth();
        if (Math.abs(extraTargetX - cannonX) < Math.abs(targetX - cannonX)) {
            targetX = extraTargetX;
        }
        double negativeTargetY = targetY - space.getHeight();
        if (Math.abs(negativeTargetY - cannonY) < Math.abs(targetY - cannonY)) {
            targetY = negativeTargetY;
        }
        double extraTargetY = targetY + space.getHeight();
        if (Math.abs(extraTargetY - cannonY) < Math.abs(targetY - cannonY)) {
            targetY = extraTargetY;
        }

        double cannonSpeed = Math.max(TARGET_SHIP_SPEED, cannonPosition.getTotalTranslationalVelocity());
        double a = Math.pow(targetVelX, 2) + Math.pow(targetVelY, 2) - Math.pow(cannonSpeed, 2);
        double b = 2 * (targetVelX * (targetX - cannonX) + targetVelY * (targetY - cannonY));
        double c = Math.pow(targetX - cannonX, 2) + Math.pow(targetY - cannonY, 2);
        double disc = Math.pow(b, 2) - 4 * a * c;
        if (disc < 0) {
            return targetPosition;
        }
        double t1 = (-b + Math.sqrt(disc)) / (2 * a);
        double t2 = (-b - Math.sqrt(disc)) / (2 * a);
        double t;
        if (t1 > 0) {
            if (t2 > 0) t = Math.min(t1, t2);
            else t = t1;
        } else {
            t = t2;
        }
        double aimX = t * targetVelX + targetX;
        double aimY = t * targetVelY + targetY;
        return new Position(aimX, aimY);
    }

    protected boolean shipNeedsEnergy(Ship ship) {
        return ship.getEnergy() < ship.getMaxEnergy() * 0.2;
    }

    // region Objects in Space
    private Set<AbstractObject> getEnergySources(Toroidal2DPhysics space, String teamName) {
        Set<AbstractObject> energySources = new HashSet<>();
        energySources.addAll(space.getBeacons());
        Set<Base> ourBases = new HashSet<>();
        for (Base base : space.getBases()) {
            if (Objects.equals(base.getTeamName(), teamName) && base.getHealingEnergy() > BASE_MIN_ENERGY_THRESHOLD) {
                ourBases.add(base);
            }
        }
        energySources.addAll(ourBases);
        return energySources;
    }

    private boolean shipShouldDumpResources(Ship ship) {
        return ship.getResources().getTotal() > BASE_RETURN_THRESHOLD;
    }

    boolean gameIsEnding(Toroidal2DPhysics space) {
        return space.getCurrentTimestep() > space.getMaxTime() * 0.99;
    }

    private Collection<AbstractObject> getAsteroidsAndEnemies(Toroidal2DPhysics space) {
        Collection<AbstractObject> targets = new HashSet<>();
        targets.addAll(getEnemyTargets(space, getTeamName()));
        Set<Asteroid> asteroids = getMineableAsteroids(space);
        targets.addAll(asteroids);
        return targets;
    }

    private Set<Base> getTeamBases(Toroidal2DPhysics space, String teamName) {
        Set<Base> results = new HashSet<>();
        for (Base base : space.getBases()) {
            if (base.getTeamName().equals(teamName)) {
                results.add(base);
            }
        }
        return results;
    }

    private Set<AbstractActionableObject> getEnemyTargets(Toroidal2DPhysics space, String teamName) {
        Set<AbstractActionableObject> enemies = new HashSet<>();
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getTeamName(), teamName)) {
                enemies.add(ship);
            }
        }
        for (Base base : space.getBases()) {
            if (!Objects.equals(base.getTeamName(), teamName)) {
                enemies.add(base);
            }
        }
        return enemies;
    }

    private Set<Asteroid> getMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> results = new HashSet<>();
        for (Asteroid asteroid : space.getAsteroids()) {
            if (asteroid.isMineable()) {
                results.add(asteroid);
            }
        }
        return results;
    }

    private Set<Asteroid> getNonMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> asteroids = new HashSet<>(space.getAsteroids());
        asteroids.removeAll(getMineableAsteroids(space));
        return asteroids;
    }

    private Set<Ship> getFriendlyShips(Toroidal2DPhysics space, Ship ship) {
        Set<Ship> results = new HashSet<>();
        for (Ship otherShip : space.getShips()) {
            if (otherShip.getTeamName().equals(ship.getTeamName()) && !otherShip.getId().equals(ship.getId())) {
                results.add(otherShip);
            }
        }
        return results;
    }

    private Set<AbstractActionableObject> getOtherShips(Toroidal2DPhysics space, UUID id) {
        Set<AbstractActionableObject> ships = new HashSet<>();
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getId(), id)) {
                ships.add(ship);
            }
        }
        return ships;
    }
    // endregion

    // region Powerups and Purchases
    /**
     * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
     * far away from the existing bases
     */
    public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                     Set<AbstractActionableObject> actionableObjects,
                                                     ResourcePile resourcesAvailable,
                                                     PurchaseCosts purchaseCosts) {

        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        long baseCount = actionableObjects.stream()
                .filter(o -> o instanceof Base)
                .count();
        final double baseBuyingDistance = (space.getWidth() * 2) / (5 * baseCount);

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

        if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY, resourcesAvailable)) {
            actionableObjects.stream()
                    .filter(actionableObject -> actionableObject instanceof Ship)
                    .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                    .ifPresent(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
        }

        return purchases;
    }

    /**
     * This is the new way to shoot (and use any other power up once they exist)
     */
    public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                           Set<AbstractActionableObject> actionableObjects) {

        HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

        for (AbstractObject actionable :  actionableObjects) {
            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Collection<AbstractObject> enemyShips = getEnemyShips(space, getTeamName());
                AbstractObject closestEnemyShip = closest(space, ship.getPosition(), enemyShips);
                if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) {
                    shoot(powerupMap, ship);
                } else if(ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)){
                    powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                }
            }
        }
        return powerupMap;
    }

    private Set<AbstractObject> getEnemyShips(Toroidal2DPhysics space, String teamName) {
        Set<AbstractObject> enemies = new HashSet<>();
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getTeamName(), teamName)) {
                enemies.add(ship);
            }
        }
        for (Base base : space.getBases()) {
            if (!base.isHomeBase() && !Objects.equals(base.getTeamName(), teamName)) {
                enemies.add(base);
            }
        }
        return enemies;
    }

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

    private void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
        if (random.nextDouble() < RANDOM_SHOOT_THRESHOLD) {
            powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
        }
    }
    // endregion
}
