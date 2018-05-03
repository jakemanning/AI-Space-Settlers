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

    /**
     * Checks if the role assignment is valid for the given state
     *
     * @param state The PlanningState before this role assignment
     * @return True if role assignment is possible under the preconditions, false otherwise
     */
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
            default:
                return false;
        }
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
        return notThirsty(state) && notHomesick(state) && !flagger && !closestToAlcove(state);
    }

    private boolean validForMiner(PlanningState state) {
        // pre: Waiter(ship) ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~NextBaseBoy(ship)
        if (state.getRoles().get(shipId) != ShipRole.WAITER) {
            return false;
        }
        return notThirsty(state) && notHomesick(state) && !closestToAlcove(state);
    }

    private boolean validForAlcoveWaiter(PlanningState state) {
        // pre: Waiter(ship) ^ Flagger(s) for some other s in ships ^ ~Thirsty(ship) ^ ~Homesick(ship) ^ ~NextBaseBoy(ship)
        if (state.getRoles().get(shipId) != ShipRole.WAITER) {
            return false;
        }
        boolean flagger = false;
        for (Ship otherShip : SpaceSearchUtil.getOurShips(state.getSpace(), state.getTeamName())) {
            if (state.getRoles().get(otherShip.getId()) == ShipRole.FLAGGER) {
                flagger = true;
            }
        }
        return flagger && notThirsty(state) && notHomesick(state) && !closestToAlcove(state);
    }

    private boolean validForWaiter(PlanningState state) {
        return true;
    }

    private boolean validForBasePlacer(PlanningState state) {
        // pre: Waiter(ship) ^ NextBaseBoy(ship) ^ ~Thirsty(ship) ^ ~Homesick(ship)
        return state.getRoles().get(shipId) == ShipRole.WAITER
                && closestToAlcove(state) && notThirsty(state) && notHomesick(state);
    }

    private boolean validForDrinker(PlanningState state) {
        // pre: Waiter(ship)
        return state.getRoles().get(shipId) == ShipRole.WAITER;
    }

    private boolean validForHomewardBound(PlanningState state) {
        // pre: Waiter(ship)
        return state.getRoles().get(shipId) == ShipRole.WAITER;
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

    private PlanningState applyFlaggerAssignment(PlanningState prevState) {
        // effect: Flagger(ship) ^ Energy(ship) -= 1500 ^ FlagScore++ ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        builder.setFlagScore(prevState.getFlagScore() + 1);

        int energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.FLAGGER);
        return builder.build();
    }

    private PlanningState applyMinerAssignment(PlanningState prevState) {
        // effect: Miner(ship) ^ ResourcesInShip(ship) += 1000 ^ Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        int resourcesInShip = prevState.getResourcesAboard().get(shipId);
        builder.setResourcesAboard(shipId, resourcesInShip + 1000);

        int energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.MINER);
        return builder.build();
    }

    private PlanningState applyAlcoveWaiterAssignment(PlanningState prevState) {
        // effect: AlcoveWaiter(ship) ^ Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        int energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.ALCOVE_WAITER);
        return builder.build();
    }

    private PlanningState applyWaiterAssignment(PlanningState prevState) {
        PlanningState.Builder builder = new PlanningState.Builder(prevState);
        builder.setRole(shipId, ShipRole.WAITER);
        return builder.build();
    }

    private PlanningState applyBasePlacerAssignment(PlanningState prevState) {
        // effect: CloseBase++, Energy(ship) -= 1500 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        builder.setCloseBases(prevState.getCloseBases() + 1);

        int energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy - 1500);

        builder.setRole(shipId, ShipRole.BASE_PLACER);
        return builder.build();
    }

    private PlanningState applyDrinkerAssignment(PlanningState prevState) {
        // effect: Energy(ship) += 1000 ^ ~Waiter(ship)
        PlanningState.Builder builder = new PlanningState.Builder(prevState);

        int energy = prevState.getShipEnergies().get(shipId);
        builder.setShipEnergy(shipId, energy + 1000);

        builder.setRole(shipId, ShipRole.DRINKER);
        return builder.build();
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
        int energy = prevState.getShipEnergies().get(shipId);
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

        builder.setRole(shipId, ShipRole.HOMEWARD_BOUND);
        return builder.build();
    }

    private boolean notThirsty(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getEnergy() > 1500;
    }

    private boolean notHomesick(PlanningState state) {
        Toroidal2DPhysics space = state.getSpace();
        Ship ship = (Ship) space.getObjectById(shipId);
        return ship.getResources().getTotal() < 5000;
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
