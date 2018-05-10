package capp7507;

import spacesettlers.actions.PurchaseTypes;
import spacesettlers.clients.ImmutableTeamInfo;
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

public class PowerupUtil {

    private final JakeTeamClient client;
    private final Random random;
    private final int MIN_BASE_DISTANCE = 100;
    private final int MAX_BASE_DISTANCE = 150;

    PowerupUtil(JakeTeamClient client, Random random) {
        this.client = client;
        this.random = random;
    }

    /**
     * How much resources that we can use at our bases
     * @param space physics
     * @param teamName team name
     * @return Collective resources at all of our bases that is available to us (not on ships)
     */
    static int teamResources(Toroidal2DPhysics space, String teamName) {
        Set<ImmutableTeamInfo> teamInfos = space.getTeamInfo();
        for (ImmutableTeamInfo teamInfo : teamInfos) {
            if (teamName.equals(teamInfo.getTeamName())) {
                ResourcePile availableResources = teamInfo.getAvailableResources();
                return availableResources.getTotal();
            }
        }
        return 0;
    }

    public HashMap<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
                                                         Set<AbstractActionableObject> actionableObjects) {
        HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
        Set<AbstractActionableObject> ships = actionableObjects.stream()
                .filter(o -> o instanceof Ship)
                .collect(Collectors.toSet());
        Set<AbstractActionableObject> bases = actionableObjects.stream()
                .filter(o -> o instanceof Base)
                .collect(Collectors.toSet());
        Position lower = SpaceSearchUtil.getLowerFlagPosition(space, client.getTeamName()).getPosition();
        Position upper = SpaceSearchUtil.getUpperFlagPosition(space, client.getTeamName()).getPosition();


        int lowBaseCount = 0;
        int highBaseCount = 0;
        for (AbstractActionableObject basis : bases) {
            double distanceToLower = space.findShortestDistance(basis.getPosition(), lower);
            double distanceToUpper = space.findShortestDistance(basis.getPosition(), upper);
            if (MIN_BASE_DISTANCE < distanceToLower && distanceToLower < MAX_BASE_DISTANCE) {
                lowBaseCount++;
            } else if (MIN_BASE_DISTANCE < distanceToUpper && distanceToUpper < MAX_BASE_DISTANCE) {
                highBaseCount++;
            }
        }

        if (lowBaseCount < 2 || highBaseCount < 2) {
            int finalLowBaseCount = lowBaseCount;
            int finalHighBaseCount = highBaseCount;

            ships.forEach(s -> {
                Ship ship = (Ship) s;
                AbstractObject lowerObj = new MadeUpObject(lower);
                AbstractObject upperObj = new MadeUpObject(upper);
                Set<AbstractObject> lowerObstructions = SpaceSearchUtil.getObstructions(space, (Ship)s, lowerObj);
                Set<AbstractObject> upperObstructions = SpaceSearchUtil.getObstructions(space, (Ship)s, upperObj);
                double distanceToLower = space.findShortestDistance(s.getPosition(), lower);
                double distanceToUpper = space.findShortestDistance(s.getPosition(), upper);

                if (finalLowBaseCount < 2 && MIN_BASE_DISTANCE < distanceToLower && distanceToLower < MAX_BASE_DISTANCE && space.isPathClearOfObstructions(s.getPosition(), lower, lowerObstructions, s.getRadius())) {
                    purchases.put(s.getId(), PurchaseTypes.BASE);
                } else if (finalHighBaseCount < 2 && MIN_BASE_DISTANCE < distanceToUpper && distanceToUpper < MAX_BASE_DISTANCE && space.isPathClearOfObstructions(s.getPosition(), upper, upperObstructions, s.getRadius())) {
                    purchases.put(s.getId(), PurchaseTypes.BASE);
                }
            });
        } else {
            if (random.nextBoolean()) {
                long baseCount = actionableObjects.stream()
                        .filter(o -> o instanceof Base)
                        .count();
                // leave some wiggle room about how far away the ship needs to be to buy a base
                final double baseDistanceFactor = 0.8;
                final double baseBuyingDistance = baseDistanceFactor * MovementUtil.maxDistance(space) / baseCount;

                for (AbstractActionableObject actionableObject : actionableObjects) {
                    if (actionableObject instanceof Ship) {
                        Ship ship = (Ship) actionableObject;

                        // how far away is this ship to a base of my team?
                        double minDistance = Double.MAX_VALUE;
                        for (AbstractActionableObject obj : bases) {
                            Base base = (Base) obj;
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

            if (random.nextBoolean()) {
                purchaseEnergy(purchases, ships);
            }

            if (random.nextBoolean()) {
                purchaseEnergyBases(purchases, bases);
            }
        }

        return purchases;
    }

    /**
     * Helper method to purchase a specific powerup
     * @param purchases set to add to
     * @param objects who wants to buy
     */
    private void purchaseEnergy(HashMap<UUID, PurchaseTypes> purchases, Set<AbstractActionableObject> objects) {
        objects.stream()
                .min(Comparator.comparingInt(AbstractActionableObject::getMaxEnergy))
                .ifPresent(obj -> purchases.put(obj.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY));
    }

    /**
     * Purchases additional energy if possible
     * @param purchases set to add to
     * @param bases who wants to buy
     */
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

    /**
     * Use our purchased powerup
     * @param actionableObjects who wants to use their powerups
     * @return a map of powerups to activate
     */
    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Set<AbstractActionableObject> actionableObjects) {
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
}