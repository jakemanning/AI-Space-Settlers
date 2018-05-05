package capp7507;

import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

public class PowerupUtil {

    private final JakeTeamClient client;
    private final Random random;
    private final int BASE_PURCHASE_PERIOD = 5000;
    private final int MIN_BASE_DISTANCE = 100;
    private final int MAX_BASE_DISTANCE = 150;

    PowerupUtil(JakeTeamClient client, Random random) {
        this.client = client;
        this.random = random;
    }

    public HashMap<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                         Set<AbstractActionableObject> actionableObjects,
                                                         ResourcePile resourcesAvailable,
                                                         PurchaseCosts purchaseCosts) {

        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        Set<AbstractActionableObject> ships = actionableObjects.stream()
                .filter(o -> o instanceof Ship)
                .collect(Collectors.toSet());
        Set<AbstractActionableObject> bases = actionableObjects.stream()
                .filter(o -> o instanceof Base)
                .collect(Collectors.toSet());

        if (space.getCurrentTimestep() < BASE_PURCHASE_PERIOD) {
            Position lower = SpaceSearchUtil.getLowerFlagPosition(space, client.getTeamName());
            Position upper = SpaceSearchUtil.getUpperFlagPosition(space, client.getTeamName());

            ships.forEach(s -> {
                double distanceToLower = space.findShortestDistance(s.getPosition(), lower);
                double distanceToUpper = space.findShortestDistance(s.getPosition(), upper);
                if (MIN_BASE_DISTANCE < distanceToLower && distanceToLower < MAX_BASE_DISTANCE || MIN_BASE_DISTANCE < distanceToUpper && distanceToUpper < MAX_BASE_DISTANCE) {
                    purchases.put(s.getId(), PurchaseTypes.BASE);
                }
            });
        } else {
            if (random.nextBoolean()) {
                ships.forEach(s -> purchases.put(s.getId(), PurchaseTypes.BASE));
            }

            if (random.nextBoolean() && ships.size() < client.getMaxNumberShips()) {
                for (AbstractActionableObject actionableObject : bases) {
                    Base base = (Base) actionableObject;
                    purchases.put(base.getId(), PurchaseTypes.SHIP);
                }
            }

            if (random.nextBoolean()) {
                purchaseEnergy(purchases, ships);
            }

            if (random.nextBoolean()) {
                purchaseEnergyBases(purchases, bases);
            }
        }

        return purchases;
    }

    private void purchaseEnergy(HashMap<UUID, PurchaseTypes> purchases, Set<AbstractActionableObject> objects) {
        objects.stream()
                .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                .ifPresent(obj -> purchases.put(obj.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
    }

    private void purchaseEnergyBases(HashMap<UUID, PurchaseTypes> purchases, Set<AbstractActionableObject> bases) {
        PurchaseTypes type;
        if (random.nextBoolean()) {
            type = PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED;
        } else {
            type = PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY;
        }

        bases.stream()
                .min(Comparator.comparingInt(a -> (int) a.getEnergy()))
                .ifPresent(base -> purchases.put(base.getId(), type));
    }

    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                    Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

        for (AbstractActionableObject actionable : actionableObjects) {
            if (actionable instanceof Base) {
                Base base = (Base) actionable;
                if (base.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_BASE_HEALING_SPEED)) {
                    System.out.println("Base double heal speed");
                    // equip the double max energy powerup
                    powerupMap.put(base.getId(), SpaceSettlersPowerupEnum.DOUBLE_BASE_HEALING_SPEED);
                }
            }

            if (actionable.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                if (actionable instanceof Ship) {
                    System.out.println("Ship max energy doubling");
                } else {
                    System.out.println("Base max energy doubling");
                }
                // equip the double max energy powerup
                powerupMap.put(actionable.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
            }
        }
        return powerupMap;
    }

    public void shutDown() {
    }

    /**
     * Determines which Powerup to buy for a given purchase session
     * Using some predefined probabilities
     * i.e. we want:
     * to purchase ships 30% of the time,
     * buy bases 50% of the time,
     * energy 20% of the time,
     */
    private static class RandomDistribution {
        // Initially this has bases as 1.0, but this will evolve over time from calls to redistribute
        static double probabilities[] = {
                1.0,  // Purchase bases
                0.0, // Purchase more ships
                0.0, // Purchase more energy
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

        static void redistribute(int index, double percentToInclude) {
            double newValueAtIndex = probabilities[index] * percentToInclude;
            double amountToDistribute = (probabilities[index] - newValueAtIndex) / (probabilities.length - 1);
            for (int i = 0, j = 0; i < probabilities.length; ++i) {
                if(index == i) {
                    probabilities[index] = newValueAtIndex;
                    continue;
                }
                probabilities[i] += amountToDistribute;
            }
        }

        static void removeAndDistribute(int index) {
            if(index == Index.MORE_BASES_INDEX.value) {
                Index.MORE_BASES_INDEX.value = null;
                if (Index.MORE_SHIPS_INDEX.value != null) {
                    Index.MORE_SHIPS_INDEX.value = 0;
                }
            } else if (index == Index.MORE_SHIPS_INDEX.value) {
                Index.MORE_SHIPS_INDEX.value = null;
            }
            Index.ENERGY_INDEX.value -= 1;

            double value = probabilities[index] / (probabilities.length - 1);
            double newProbabilites[] = new double[probabilities.length - 1];

            for (int i = 0, j = 0; i < probabilities.length; ++i) {
                if(index == i) {
                    continue;
                }
                newProbabilites[j] = probabilities[i] + value;
                ++j;
            }
            probabilities = newProbabilites;
        }

        /**
         * What we're going to end up buying
         */
        enum Index {
            INVALID(-1), MORE_BASES_INDEX(0), MORE_SHIPS_INDEX(1), ENERGY_INDEX(2);

            Integer value;

            Index(Integer i) {
                this.value = i;
            }

            public Integer getValue() {
                return value;
            }
        }
    }
}