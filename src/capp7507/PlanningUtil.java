package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.*;
import java.util.stream.Collectors;

class PlanningUtil {
    public static final int GOAL_BUFFER_SPACE = 30;
    private final String teamName;
    private Map<UUID, ShipRole> currentRoles = new HashMap<>();
    private Map<UUID, AbstractObject> currentGoals = new HashMap<>();

    PlanningUtil(String teamName) {
        this.teamName = teamName;
    }

    List<RoleAssignment> search(PlanningState initialState) {
        // TODO
        // returns a list of role assignments
        return new ArrayList<>();
    }

    ShipRole getRole(Ship ship) {
        return currentRoles.get(ship.getId());
    }

    void setRole(Ship ship, ShipRole role) {
        currentRoles.put(ship.getId(), role);
    }

    Set<AbstractObject> otherShipGoals(Ship ship) {
        return currentGoals.keySet().stream()
                .filter(uuid -> !uuid.equals(ship.getId()))
                .map(uuid -> currentGoals.get(uuid))
                .collect(Collectors.toSet());
    }

    void assignClosestFlagCollectors(Toroidal2DPhysics space) {
        Position targetFlagPosition = SpaceSearchUtil.getTargetFlag(space, teamName).getPosition();
        Set<Ship> ourShips = SpaceSearchUtil.getOurShips(space, teamName);
        Ship flagCollector = MovementUtil.closest(space,
                targetFlagPosition,
                ourShips);
        ourShips.remove(flagCollector);
        Ship secondFlagCollector = MovementUtil.closest(space,
                targetFlagPosition,
                ourShips);
        assignFlagCollectors(space, flagCollector, secondFlagCollector);
    }

    private void assignFlagCollectors(Toroidal2DPhysics space, Ship collector1, Ship collector2) {
        for (Ship ship : SpaceSearchUtil.getOurShips(space, teamName)) {
            UUID shipId = ship.getId();
            if (currentRoles.get(shipId) == ShipRole.FLAGGER) {
                currentRoles.put(shipId, ShipRole.MINER);
            }
            UUID collector1Id = collector1.getId();
            UUID collector2Id = collector2.getId();
            if (collector1Id.equals(shipId) || collector2Id.equals(shipId)) {
                currentRoles.put(collector1Id, ShipRole.FLAGGER);
            }
        }
    }

    AbstractObject flagTarget(Toroidal2DPhysics space, Ship ship) {
        AbstractObject target;
        Flag targetFlag = SpaceSearchUtil.getTargetFlag(space, teamName);
        if (otherShipGoals(ship).contains(targetFlag)) {
            Position lowerFlagPosition = SpaceSearchUtil.getLowerFlagPosition(space, teamName);
            Position upperFlagPosition = SpaceSearchUtil.getUpperFlagPosition(space, teamName);
            double lowerPositionDistance = space.findShortestDistance(lowerFlagPosition, targetFlag.getPosition());
            double upperPositionDistance = space.findShortestDistance(upperFlagPosition, targetFlag.getPosition());
            Position targetPosition;
            if (lowerPositionDistance > upperPositionDistance) {
                targetPosition = lowerFlagPosition;
            } else {
                targetPosition = upperFlagPosition;
            }
            target = new AbstractObject(0, 10, targetPosition) {
                @Override
                public AbstractObject deepClone() {
                    return this;
                }
            };
        } else {
            target = targetFlag;
        }
        return target;
    }
}
