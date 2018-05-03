package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Flag;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

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

    public boolean isValid(PlanningState state) {
        switch (role) {
            case FLAGGER:
                return validForFlagger(state);
            case MINER:
                return validForMiner(state);
            case ALCOVE_WAITER:
                return validForAlcoveWaiter(state);
            case WAITER:
                return validForWaiter(state);
            case BASE_PLACER:
                return validForBasePlacer(state);
            case DRINKER:
                return validForDrinker(state);
            case HOMEWARD_BOUND:
                return validForHomewardBound(state);
        }
        return false;
    }

    private boolean validForFlagger(PlanningState state) {
        // pre: AlcoveWaiter(ship) ^ Closest(ship, flag) ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~Flagger(s) for all s in ships ^ ~NextBaseBoy(ship)
        if (state.getRoles().get(shipId) != ShipRole.ALCOVE_WAITER) {
            return false;
        }
        Flag flag = SpaceSearchUtil.getTargetFlag(state.getSpace(), state.getTeamName());
        Set<Ship> ourShips = SpaceSearchUtil.getOurShips(state.getSpace(), state.getTeamName());
        Ship closest = MovementUtil.closest(state.getSpace(), flag.getPosition(), ourShips);
        if (!shipId.equals(closest.getId())) {
            return false;
        }
        boolean flagger = false;
        for (Ship otherShip : SpaceSearchUtil.getOurShips(state.getSpace(), state.getTeamName())) {
            if (state.getRoles().get(otherShip.getId()) == ShipRole.FLAGGER) {
                flagger = true;
            }
        }
        return !thirsty(state) && !homesick(state) && !flagger && !closestToAlcove(state);
    }

    private boolean validForMiner(PlanningState state) {
        // pre: Waiter(ship) ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~NextBaseBoy(ship)
        if (state.getRoles().get(shipId) != ShipRole.WAITER) {
            return false;
        }
        return !thirsty(state) && !homesick(state) && !closestToAlcove(state);
    }

    private boolean validForAlcoveWaiter(PlanningState state) {
        if (state.getRoles().get(shipId) != ShipRole.WAITER) {
            return false;
        }
        boolean flagger = false;
        for (Ship otherShip : SpaceSearchUtil.getOurShips(state.getSpace(), state.getTeamName())) {
            if (state.getRoles().get(otherShip.getId()) == ShipRole.FLAGGER) {
                flagger = true;
            }
        }
        return flagger && !thirsty(state) && !homesick(state) && !closestToAlcove(state);
    }

    private boolean validForWaiter(PlanningState state) {
        return true;
    }

    private boolean validForBasePlacer(PlanningState state) {
        return state.getRoles().get(shipId) == ShipRole.WAITER
                && closestToAlcove(state) && !thirsty(state) && !homesick(state);
    }

    private boolean validForDrinker(PlanningState state) {
        return state.getRoles().get(shipId) == ShipRole.WAITER;
    }

    private boolean validForHomewardBound(PlanningState state) {
        return state.getRoles().get(shipId) == ShipRole.WAITER;
    }

    /**
     * Applies the effect of this role assignment to a copy of the given state and returns the resulting state
     *
     * @param prevState
     * @return
     */
    public PlanningState nextState(PlanningState prevState) {
        PlanningState nextState = prevState.copy();
        // TODO
        switch (role) {
            case FLAGGER:
                break;
            case MINER:
                break;
            case ALCOVE_WAITER:
                break;
            case WAITER:
                break;
            case BASE_PLACER:
                break;
            case DRINKER:
                break;
            case HOMEWARD_BOUND:
                break;
        }
        return nextState;
    }

    private boolean thirsty(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getEnergy() < 1500;
    }

    private boolean homesick(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getResources().getTotal() > 5000;
    }

    private boolean closestToAlcove(PlanningState state) {
        Position alcovePosition = PlanningUtil.powerupLocation;
        Toroidal2DPhysics space = state.getSpace();
        Set<Ship> ourShips = SpaceSearchUtil.getOurShips(space, state.getTeamName());
        AbstractObject closest = MovementUtil.closest(space, alcovePosition, ourShips);
        return shipId.equals(closest.getId());
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
