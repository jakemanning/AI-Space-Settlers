package capp7507;

import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.objects.weapons.AbstractWeapon;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.*;
import java.util.stream.DoubleStream;

import static capp7507.SpaceSearchUtil.getEnemyTargets;
import static capp7507.SpaceSearchUtil.getTeamBases;

public class PowerupUtil {
    private static final double RANDOM_SHOOT_THRESHOLD = 0.35;
    private static final double SHIP_NEEDS_ENERGY_FACTOR = 0.2;
    private static final int SHIELD_RADIUS = 3;
    private static final double MAX_SHOT_ANGLE = Math.PI / 12;
    private static final int MAX_SHOT_DISTANCE = 100;

    private final JakeTeamClient client;
    private final Random random;
    private Set<UUID> shieldedObjects = new HashSet<>();

    PowerupUtil(JakeTeamClient client, Random random) {
        this.client = client;
        this.random = random;
    }

    public HashMap<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                         Set<AbstractActionableObject> actionableObjects,
                                                         ResourcePile resourcesAvailable,
                                                         PurchaseCosts purchaseCosts) {

        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        int action = RandomDistribution.biasTowards(random);

        if (action == RandomDistribution.Index.MORE_SHIPS.getValue() && purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable)) {
            long shipCount = actionableObjects.stream()
                    .filter(o -> o instanceof Ship)
                    .count();

            if (shipCount < client.getMaxNumberShips()) {
                for (AbstractActionableObject actionableObject : actionableObjects) {
                    if (actionableObject instanceof Base) {
                        Base base = (Base) actionableObject;
                        purchases.put(base.getId(), PurchaseTypes.SHIP);
                    }
                }
            }
        } else if (action == RandomDistribution.Index.MORE_BASES.getValue() && purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
            long baseCount = actionableObjects.stream()
                    .filter(o -> o instanceof Base)
                    .count();
            // leave some wiggle room about how far away the ship needs to be to buy a base
            final double baseDistanceFactor = 0.8;
            final double baseBuyingDistance = baseDistanceFactor * MovementUtil.maxDistance(space) / baseCount;

            for (AbstractActionableObject actionableObject : actionableObjects) {
                if (actionableObject instanceof Ship) {
                    Ship ship = (Ship) actionableObject;
                    Set<Base> bases = getTeamBases(space, client.getTeamName());

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
        } else if (action == RandomDistribution.Index.ENERGY.getValue() && purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY, resourcesAvailable)) {
            actionableObjects.stream()
                    .filter(actionableObject -> actionableObject instanceof Ship)
                    .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                    .ifPresent(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
        } else if (action == RandomDistribution.Index.SHIELDS.getValue() && purchaseCosts.canAfford(PurchaseTypes.POWERUP_SHIELD, resourcesAvailable)) {
            // Ships are more important; buy their shields first
            actionableObjects.stream()
                    .filter(actionableObject -> actionableObject instanceof Ship)
                    .filter(ship -> !ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD))
                    .forEach(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_SHIELD));

            // If we have any money left, purchase shields for bases
            if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_SHIELD, resourcesAvailable)) {
                actionableObjects.stream()
                        .filter(actionableObject -> actionableObject instanceof Base)
                        .filter(base -> !base.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD))
                        .limit(3) // Only purchases 3 base shields at a time to save money
                        .forEach(base -> purchases.put(base.getId(), PurchaseTypes.POWERUP_SHIELD));
            }
        } else if (action == RandomDistribution.Index.INVALID.getValue()) {
            System.out.println("Invalid random distribution. This shouldn't happen.");
        }

        return purchases;
    }

    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                    Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

        for (AbstractObject actionable : actionableObjects) {
            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Set<AbstractActionableObject> enemyShips = getEnemyTargets(space, client.getTeamName());
                AbstractObject closestEnemyShip = MovementUtil.closest(space, ship.getPosition(), enemyShips);
                if (ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) { // protect ship if we're in position and do not need energy
                    if (shieldedObjects.contains(ship.getId()) != ship.isShielded()) { // Only if the status of the ship has changed
                        powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.TOGGLE_SHIELD);
                    }
                } else if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) { // shoot if we're in position and do not need energy
                    shoot(powerupMap, ship);
                } else if (ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                    // equip the double max energy powerup
                    powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                }
            }
        }
        return powerupMap;
    }

    /**
     * Whether the ship needs more energy or not.
     * Compares the ship's energy level as a percentage of its max energy level with {@value SHIP_NEEDS_ENERGY_FACTOR}
     *
     * @param ship The ship that may need more energy
     * @return True if the ship needs more energy, false otherwise
     */
    private boolean shipNeedsEnergy(Ship ship) {
        return ship.getEnergy() < ship.getMaxEnergy() * SHIP_NEEDS_ENERGY_FACTOR;
    }

    void shieldIfNeeded(Toroidal2DPhysics space, AbstractActionableObject actionable) {
        if (shouldShield(space, actionable, space.getAllObjects())) {
            shieldedObjects.add(actionable.getId());
        } else if (actionable.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) {
            shieldedObjects.remove(actionable.getId());
        }
    }

    /**
     * Determine if a weapon is nearby
     *
     * @param space   physics
     * @param obj     Ship to detect from
     * @param objects All possible objects in space
     * @return true if a weapon is within {@value SHIELD_RADIUS} * ship's radius
     */
    private boolean shouldShield(Toroidal2DPhysics space, AbstractActionableObject obj, Set<AbstractObject> objects) {
        if (!obj.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) {
            return false;
        }

        boolean weaponIsClose = false;
        for (AbstractObject object : objects) {
            if (!(object instanceof AbstractWeapon)) {
                continue;
            }

            AbstractWeapon weapon = (AbstractWeapon) object;
            double weaponDistance = space.findShortestDistance(obj.getPosition(), weapon.getPosition());
            if (isEnemyWeapon(weapon) && weaponDistance < obj.getRadius() * SHIELD_RADIUS) {
                weaponIsClose = true;
                break;
            }
        }
        return weaponIsClose;
    }

    /**
     * Determines whether a given actionableObject is an enemy weapon
     *
     * @param weapon object to check
     * @return Whether the object is an enemy weapon
     */
    private boolean isEnemyWeapon(AbstractWeapon weapon) {
        return !weapon.getFiringShip().getTeamName().equals(client.getTeamName());
    }

    /**
     * Determine if the ship at currentPosition is in position to shoot the target
     * <p>
     * If the target is within a distance of {@value MAX_SHOT_DISTANCE} and at an
     * angle less than {@value MAX_SHOT_ANGLE} from the ship's current orientation then
     * the ship is considered in position to shoot the target.
     *
     * @param space           physics
     * @param currentPosition The current position of a ship
     * @param target          The potential target for the ship to shoot
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
     *
     * @param powerupMap A map from ship IDs to powerup types that is added to when shooting
     * @param ship       The ship that will shoot
     */
    private void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
        if (random.nextDouble() < RANDOM_SHOOT_THRESHOLD) {
            powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
        }
    }

    private static class RandomDistribution {
        static double probabilities[] = {
                0.3, // Purchase more ships
                0.25, // Purchase more bases
                0.3, // Purchase more energy
                0.15, // Purchase shields ability
        };

        public RandomDistribution() {
            assert (DoubleStream.of(probabilities).sum() == 1);
        }

        static int biasTowards(Random random) {
            double roll = random.nextDouble();
            double total = 0;
            int index = 0;
            for (double d : probabilities) {
                total += d;
                if (roll < total) {
                    return index;
                }
                index += 1;
            }
            return -1; // This shouldn't ever happen
        }

        enum Index {
            INVALID(-1), MORE_SHIPS(0), MORE_BASES(1), ENERGY(2), SHIELDS(3);

            private final int value;

            Index(int i) {
                this.value = i;
            }

            public int getValue() {
                return value;
            }
        }
    }
}