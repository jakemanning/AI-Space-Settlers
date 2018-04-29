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

import java.util.*;
import java.util.stream.DoubleStream;

import static capp7507.SpaceSearchUtil.getEnemyTargets;
import static capp7507.SpaceSearchUtil.getTeamBases;

public class PowerupUtil {
    private static final int SHIELD_RADIUS = 3;

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
                for (AbstractActionableObject ignored : enemyShips) {
                    if (ship.isValidPowerup(SpaceSettlersPowerupEnum.TOGGLE_SHIELD)) { // protect ship if we're in position and do not need energy
                        if (shieldedObjects.contains(ship.getId()) != ship.isShielded()) { // Only if the status of the ship has changed
                            powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.TOGGLE_SHIELD);
                        }
                    } else if (ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                        // equip the double max energy powerup
                        powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                    }
                }
            }
        }
        return powerupMap;
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

    public void shutDown() {
    }

    /**
     * Determines which Powerup to buy for a given purchase session
     * Using some predefined probabilities
     * i.e. we want:
     * to purchase ships 30% of the time,
     * buy bases 25% of the time,
     * energy 30% of the time,
     * and shields 15% of the time
     */
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

        /**
         * Where all of the magic happens
         * Uses the probabilities array to bias towards a value
         *
         * @param random random
         * @return which {@link Index} we want to bias towards
         */
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

        /**
         * What we're going to end up buying
         */
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