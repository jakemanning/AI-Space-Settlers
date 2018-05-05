package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.*;

class PlanningUtil {
    public static Position powerupLocation = null;
    private final String teamName;
    private Map<UUID, ShipRole> currentRoles = new HashMap<>();
    private int flagScore = 0;

    PlanningUtil(String teamName) {
        this.teamName = teamName;
    }

    public PlanningState currentState(Toroidal2DPhysics space) {
        Set<Ship> ourShips = SpaceSearchUtil.getOurShips(space, teamName);
        int shipCount = ourShips.size();
        Position flagPosition = SpaceSearchUtil.getTargetFlag(space, teamName).getPosition();
        int closeBases = (int) SpaceSearchUtil.getTeamBases(space, teamName).stream()
                .filter(base -> space.findShortestDistance(flagPosition, base.getPosition()) < space.getWidth() / 3)
                .count();
        int resourcesAvailable = PowerupUtil.teamResources(space, teamName);
        Map<UUID, Double> shipEnergies = new HashMap<>();
        Map<UUID, Integer> resourcesAboard = new HashMap<>();
        for (Ship ship : ourShips) {
            shipEnergies.put(ship.getId(), ship.getEnergy());
            resourcesAboard.put(ship.getId(), ship.getResources().getTotal());
        }
        for (Ship ship : ourShips) {
            currentRoles.putIfAbsent(ship.getId(), ShipRole.WAITER);
        }
        Map<UUID, Position> positions = new HashMap<>();
        for (Ship ship : ourShips) {
            positions.put(ship.getId(), ship.getPosition());
        }

        return new PlanningState(space, flagScore, shipCount, closeBases, resourcesAvailable,
                shipEnergies, resourcesAboard, currentRoles, positions);
    }

    List<RoleAssignment> search(PlanningState initialState) {
        Queue<PlanningState> statesQueue = new LinkedList<>();
        List<RoleAssignment> allAssignments = allAssignments(initialState);
        Map<PlanningState, List<RoleAssignment>> paths = new HashMap<>();
        statesQueue.add(initialState);
        paths.put(initialState, new ArrayList<>());
        while (!statesQueue.isEmpty() && paths.size() < 4000) {
            PlanningState state = statesQueue.poll();
            for (RoleAssignment assignment : allAssignments) {
                List<RoleAssignment> path = paths.get(state);
                if (assignment.isValid(state, rolesOfOtherShips(assignment.getShipId(), state))) {
                    PlanningState resultState = assignment.nextState(state);
                    if (!paths.containsKey(resultState)) {
                        statesQueue.add(resultState);
                    }
                    List<RoleAssignment> resultPath = new ArrayList<>(path);
                    resultPath.add(assignment);
                    paths.put(resultState, resultPath);
                    if (resultState.getFlagScore() >= initialState.getFlagScore() + 1) {
                        return paths.get(resultState);
                    }
                }
            }
        }
        return null;
    }

    private Map<UUID, ShipRole> rolesOfOtherShips(UUID shipId, PlanningState state) {
        Map<UUID, ShipRole> roles = new HashMap<>(state.getRoles());
        roles.remove(shipId);
        return roles;
    }

    private List<RoleAssignment> allAssignments(PlanningState state) {
        List<RoleAssignment> allAssignments = new ArrayList<>();
        Set<Ship> ourShips = SpaceSearchUtil.getOurShips(state.getSpace(), state.getTeamName());
        for (Ship ship : ourShips) {
            for (ShipRole role : ShipRole.values()) {
                RoleAssignment roleAssignment = new RoleAssignment(ship.getId(), role);
                allAssignments.add(roleAssignment);
            }
        }
        return allAssignments;
    }

    ShipRole getRole(UUID shipId) {
        ShipRole role = currentRoles.get(shipId);
        return role == null ? ShipRole.WAITER : role;
    }

    boolean anyWaiting(Toroidal2DPhysics space) {
        for (Ship ship : SpaceSearchUtil.getOurShips(space, teamName)) {
            currentRoles.putIfAbsent(ship.getId(), ShipRole.WAITER);
            ShipRole role = currentRoles.get(ship.getId());
            if (role == ShipRole.WAITER) {
                return true;
            }
        }
        return false;
    }

    void setRole(UUID shipId, ShipRole role) {
        currentRoles.put(shipId, role);
    }

    void clearRoles(Toroidal2DPhysics space) {
        for (Ship ship : SpaceSearchUtil.getOurShips(space, teamName)) {
            currentRoles.put(ship.getId(), ShipRole.WAITER);
        }
    }

    public void incrementFlagScore() {
        flagScore++;
    }
}
