package capp7507;

import spacesettlers.actions.PurchaseCosts;
import spacesettlers.actions.PurchaseTypes;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static capp7507.SpaceSearchUtil.getEnemyTargets;

public class PowerupUtil {

    private final JakeTeamClient client;
    private final Random random;

    PowerupUtil(JakeTeamClient client, Toroidal2DPhysics space, Random random) {
        this.client = client;
        this.random = random;
    }

    public HashMap<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                         Set<AbstractActionableObject> actionableObjects,
                                                         ResourcePile resourcesAvailable,
                                                         PurchaseCosts purchaseCosts) {

        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        int action = RandomDistribution.biasTowards(random);
        Set<AbstractActionableObject> ships = actionableObjects.stream()
                .filter(o -> o instanceof Ship)
                .collect(Collectors.toSet());
        Set<AbstractActionableObject> bases = actionableObjects.stream()
                .filter(o -> o instanceof Base)
                .collect(Collectors.toSet());

        // We always want to buy that first base, and we might want to buy that 2-4th base. No more after that since we don't want to get in the way of the enemy team
//        if (PlanningUtil.powerupLocation == null)
        if (RandomDistribution.Index.MORE_BASES_INDEX.value != null && action == RandomDistribution.Index.MORE_BASES_INDEX.getValue() && purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
            Position nextBaseLocation;
            if (SpaceSearchUtil.targetFlagIsOnLeftSide) {
                nextBaseLocation = SpaceSearchUtil.baseLeftHalfPosition.deepCopy();
            } else {
                nextBaseLocation = SpaceSearchUtil.baseRightHalfPosition.deepCopy();
            }

            if (bases.size() == 1) {
                RandomDistribution.redistribute(RandomDistribution.Index.MORE_BASES_INDEX.value, 3/4);
            } else if (bases.size() == 2) {
                nextBaseLocation.setY(space.getHeight() * 0.25);
                RandomDistribution.redistribute(RandomDistribution.Index.MORE_BASES_INDEX.value, 2/3);
            } else if (bases.size() == 3) {
                nextBaseLocation.setY(space.getHeight() * 0.75);
                RandomDistribution.redistribute(RandomDistribution.Index.MORE_BASES_INDEX.value, 2/3);
            } else if (bases.size() == 4) {
                nextBaseLocation.setX(space.getWidth() * 0.25);
                RandomDistribution.removeAndDistribute(RandomDistribution.Index.MORE_BASES_INDEX.value);
            }
            System.out.println("We need to buy a base");
//            PlanningUtil.powerupLocation = nextBaseLocation;
        // We're unable to have more than 6 ships. If we have less, we might buy this
        } else if (RandomDistribution.Index.MORE_SHIPS_INDEX.value != null && action == RandomDistribution.Index.MORE_SHIPS_INDEX.getValue() && purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable)) {
            if (ships.size() < client.getMaxNumberShips()) {
                for (AbstractActionableObject actionableObject : bases) {
                    Base base = (Base) actionableObject;
                    purchases.put(base.getId(), PurchaseTypes.SHIP);
                }
                RandomDistribution.redistribute(RandomDistribution.Index.MORE_SHIPS_INDEX.value, 0.1);
            } else if (ships.size() == client.getMaxNumberShips()) {
                RandomDistribution.removeAndDistribute(RandomDistribution.Index.MORE_SHIPS_INDEX.value);
                purchaseEnergy(purchases, ships);
            }
        // If we have six ships or we didn't decide to purchase bases/ships, buy some energy
        } else if (action == RandomDistribution.Index.ENERGY_INDEX.getValue() && purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY, resourcesAvailable)) {
            purchaseEnergy(purchases, ships);
        } else if (action == RandomDistribution.Index.INVALID.getValue()) {
            System.out.println("Invalid random distribution. This shouldn't happen.");
        }

        return purchases;
    }

    private void purchaseEnergy(HashMap<UUID, PurchaseTypes> purchases, Set<AbstractActionableObject> ships) {
        ships.stream()
                .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                .ifPresent(ship -> purchases.put(ship.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
    }

    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
                                                    Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

        for (AbstractObject actionable : actionableObjects) {
            if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                Set<AbstractActionableObject> enemyShips = getEnemyTargets(space, client.getTeamName());
                for (AbstractActionableObject ignored : enemyShips) {
                    if (ship.isValidPowerup(SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY)) {
                        // equip the double max energy powerup
                        powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
                    }
                }
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