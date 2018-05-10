package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Asteroid;
import spacesettlers.objects.Base;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class RoleAssignment {
    private UUID shipId;
    private ShipRole role;

    public RoleAssignment(UUID shipId, ShipRole role) {
        this.shipId = shipId;
        this.role = role;
    }

    public UUID getShipId() {
        return shipId;
    }

    public ShipRole getRole() {
        return role;
    }

    /**
     * Checks if the role assignment is valid for the given state
     *
     * @param state The PlanningState before this role assignment
     * @return True if role assignment is possible under the preconditions, false otherwise
     */
    public boolean isValid(PlanningState state, Map<UUID, ShipRole> otherAssignmentsThisTurn) {
        switch (role) {
            case FLAGGER:
                return validForFlagger(state, otherAssignmentsThisTurn);
            case MINER:
                return validForMiner(state);
            case ALCOVE_WAITER:
                return validForAlcoveWaiter(state, otherAssignmentsThisTurn);
            case WAITER:
                return validForWaiter(state);
            case BASE_PLACER:
                return validForBasePlacer(state, otherAssignmentsThisTurn);
            case DRINKER:
                return validForDrinker(state);
            case HOMEWARD_BOUND:
                return validForHomewardBound(state);
            default:
                return false;
        }
    }

    /**
     * Applies the effect of this role assignment to a copy of the given state and returns the resulting state
     *
     * @param prevState The PlanningState before this role assignment
     * @return The PlanningState after this role assignment
     */
    public PlanningState nextState(PlanningState prevState) {
        PlanningState nextState;
        switch (role) {
            case FLAGGER:
                nextState = applyFlaggerAssignment(prevState);
                break;
            case MINER:
                nextState = applyMinerAssignment(prevState);
                break;
            case ALCOVE_WAITER:
                nextState = applyAlcoveWaiterAssignment(prevState);
                break;
            case WAITER:
                nextState = applyWaiterAssignment(prevState);
                break;
            case BASE_PLACER:
                nextState = applyBasePlacerAssignment(prevState);
                break;
            case DRINKER:
                nextState = applyDrinkerAssignment(prevState);
                break;
            case HOMEWARD_BOUND:
                nextState = applyHomewardBoundAssignment(prevState);
                break;
            default:
                nextState = prevState;
        }
        return nextState;
    }

    private boolean validForFlagger(PlanningState state, Map<UUID, ShipRole> otherAssignmentsThisTurn) {
        // pre: AlcoveWaiter(ship) ^ Closest(ship, flag) ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~Flagger(s) for all s in ships ^ ~NextBaseBoy(ship)
        if (state.getRole(shipId) != ShipRole.ALCOVE_WAITER) {
            return false;
        }

        boolean flagger = false;
        for (ShipRole role : otherAssignmentsThisTurn.values()) {
            if (role == ShipRole.FLAGGER) {
                flagger = true;
            }
        }
        return !thirsty(state) && !homesick(state) && !flagger;
    }

    private PlanningState applyFlaggerAssignment(PlanningState prevState) {
        // effect: Flagger(ship) ^ Energy(ship) -= 1500 ^ FlagScore++ ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        builder.setFlagScore(prevState.getFlagScore() + 1);

        double energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        Base closestBase = MovementUtil.closest(prevState.getSpace(), prevState.getPosition(shipId),
                SpaceSearchUtil.getTeamBases(prevState.getSpace(), prevState.getTeamName()));
        builder.setPosition(shipId, closestBase.getPosition());

        builder.setRole(shipId, ShipRole.FLAGGER);
        return builder.build();
    }

    private boolean validForMiner(PlanningState state) {
        // pre: Waiter(ship) ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~NextBaseBoy(ship)
        if (state.getRole(shipId) == ShipRole.ALCOVE_WAITER) {
            return false;
        }
        return !thirsty(state) && !homesick(state);
    }

    private PlanningState applyMinerAssignment(PlanningState prevState) {
        // effect: Miner(ship) ^ ResourcesInShip(ship) += 1000 ^ Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        Asteroid closest = MovementUtil.closest(prevState.getSpace(), prevState.getPosition(shipId),
                SpaceSearchUtil.getMineableAsteroids(prevState.getSpace()));

        int resourcesInShip = prevState.getResourcesAboard().get(shipId);
        builder.setResourcesAboard(shipId, resourcesInShip + closest.getResources().getTotal());

        builder.setPosition(shipId, closest.getPosition());

        double energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.MINER);
        return builder.build();
    }

    private boolean validForAlcoveWaiter(PlanningState state, Map<UUID, ShipRole> otherAssignmentsThisTurn) {
        // pre: Waiter(ship) ^ AlcoveWaiter(s) for no more than one other s in ships ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~NextBaseBoy(ship)
        int otherAlcoveWaiters = 0;
        for (ShipRole role : otherAssignmentsThisTurn.values()) {
            if (role == ShipRole.ALCOVE_WAITER || role == ShipRole.BASE_PLACER) {
                otherAlcoveWaiters++;
            }
        }
        return !thirsty(state) && !homesick(state) && otherAlcoveWaiters < 2;
    }

    private PlanningState applyAlcoveWaiterAssignment(PlanningState prevState) {
        // effect: AlcoveWaiter(ship) ^ Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        double energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.ALCOVE_WAITER);
        return builder.build();
    }

    private boolean validForWaiter(PlanningState state) {
        return false;
    }

    private PlanningState applyWaiterAssignment(PlanningState prevState) {
        PlanningState.Builder builder = new PlanningState.Builder(prevState);
        builder.setRole(shipId, ShipRole.WAITER);
        return builder.build();
    }

    private boolean validForBasePlacer(PlanningState state, Map<UUID, ShipRole> otherAssignmentsThisTurn) {
        // pre: Waiter(ship) ^ NextBaseBoy(ship) ^ ~Thirsty(ship) ^ ~Homesick(ship)
        int otherAlcoveWaiters = 0;
        for (ShipRole role : otherAssignmentsThisTurn.values()) {
            if (role == ShipRole.ALCOVE_WAITER || role == ShipRole.BASE_PLACER) {
                otherAlcoveWaiters++;
            }
        }
        return PlanningUtil.powerupLocation != null && closestToAlcove(state) &&
                !thirsty(state) && !homesick(state) && otherAlcoveWaiters < 2;
    }

    private PlanningState applyBasePlacerAssignment(PlanningState prevState) {
        // effect: CloseBase++, Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        builder.setCloseBases(prevState.getCloseBases() + 1);

        double energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setPosition(shipId, PlanningUtil.powerupLocation);

        builder.setRole(shipId, ShipRole.BASE_PLACER);
        return builder.build();
    }

    private boolean validForDrinker(PlanningState state) {
        // pre: Waiter(ship) ^ Thirsty(ship)
        return thirsty(state);
    }

    private PlanningState applyDrinkerAssignment(PlanningState prevState) {
        // effect: Energy(ship) += 1000 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        double energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy + 1000);

        Toroidal2DPhysics space = prevState.getSpace();
        Position prevPosition = prevState.getPosition(shipId);
        String teamName = prevState.getTeamName();
        AbstractObject closest = MovementUtil.closest(space, prevPosition, SpaceSearchUtil.getEnergySources(space, teamName));
        builder.setPosition(shipId, closest.getPosition());

        builder.setRole(shipId, ShipRole.DRINKER);
        return builder.build();
    }

    private boolean validForHomewardBound(PlanningState state) {
        // pre: Waiter(ship)
        return homesick(state);
    }

    private PlanningState applyHomewardBoundAssignment(PlanningState prevState) {
        //effect: Resources += ResourcesInShip(ship) ^ ResourcesInShip(ship) = 0 ^ ~Waiter(ship)
        //when CloseBases > 2 : Energy(ship) += 2000
        //when CloseBases > 1 : Energy(ship) += 1500
        //when CloseBases > 0 : Energy(ship) += 1000
        //when CloseBases = 0 : Energy(ship) += 500

        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        int resourcesInShip = prevState.getResourcesAboard().get(shipId);
        builder.setResourcesAvailable(prevState.getResourcesAvailable() + resourcesInShip);

        builder.setResourcesAboard(shipId, 0);

        int closeBases = prevState.getCloseBases();
        double energy = prevState.getShipEnergies().get(shipId);
        if (closeBases > 2) {
            energy += 2000;
        } else if (closeBases > 1) {
            energy += 1500;
        } else if (closeBases > 0) {
            energy += 1000;
        } else {
            energy += 500;
        }
        builder.setShipEnergy(shipId, energy);

        Toroidal2DPhysics space = prevState.getSpace();
        Position prevPosition = prevState.getPosition(shipId);
        String teamName = prevState.getTeamName();
        AbstractObject closest = MovementUtil.closest(space, prevPosition, SpaceSearchUtil.getTeamBases(space, teamName));
        builder.setPosition(shipId, closest.getPosition());

        builder.setRole(shipId, ShipRole.HOMEWARD_BOUND);
        return builder.build();
    }

    private UUID closestToFlag(PlanningState state, Position target) {
        double closest = Double.MAX_VALUE;
        UUID ship = null;
        for (Map.Entry<UUID, Position> entry : state.getPositions().entrySet()) {
            if (state.getRoles().get(entry.getKey()) != ShipRole.ALCOVE_WAITER) {
                continue;
            }
            double distance = state.getSpace().findShortestDistance(entry.getValue(), target);
            if (distance < closest) {
                closest = distance;
                ship = entry.getKey();
            }
        }
        return ship;
    }

    private boolean thirsty(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getEnergy() < 1500;
    }

    private boolean homesick(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getResources().getTotal() > 1500;
    }

    private boolean closestToAlcove(PlanningState state) {
        Position lowerFlagPosition = SpaceSearchUtil.getLowerFlagPosition(state.getSpace(), state.getTeamName()).getPosition();
        Position upperFlagPosition = SpaceSearchUtil.getUpperFlagPosition(state.getSpace(), state.getTeamName()).getPosition();
        Toroidal2DPhysics space = state.getSpace();
        Set<Ship> waiters = SpaceSearchUtil.getOurShips(space, state.getTeamName()).stream()
                .filter(s -> state.getRole(s.getId()) == ShipRole.WAITER)
                .collect(Collectors.toSet());
        if (lowerFlagPosition != null) {
            AbstractObject lowerClosest = MovementUtil.closest(space, lowerFlagPosition, waiters);
            if (shipId.equals(lowerClosest.getId())) {
                return true;
            }
        }
        if (upperFlagPosition != null) {
            AbstractObject upperClosest = MovementUtil.closest(space, upperFlagPosition, waiters);
            return shipId.equals(upperClosest.getId());
        }
        return false;
    }

    @Override
    public String toString() {
        return "RoleAssignment{" +
                "shipId=" + shipId +
                ", role=" + role +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleAssignment)) return false;
        RoleAssignment that = (RoleAssignment) o;
        return Objects.equals(shipId, that.shipId) &&
                role == that.role;
    }

    @Override
    public int hashCode() {

        return Objects.hash(shipId, role);
    }
}
