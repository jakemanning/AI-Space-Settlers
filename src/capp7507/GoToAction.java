package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.Objects;

/**
 * High level action to move a ship to the location of a target object
 */
public class GoToAction {
    private Toroidal2DPhysics space;
    private final Ship ship;
    private ShipRole role;
    private final AbstractObject target;

    public GoToAction(Toroidal2DPhysics space, Ship ship, ShipRole role, AbstractObject target) {
        this.space = space;
        this.ship = ship;
        this.role = role;
        this.target = target;

    }

    public Route getRoute() {
        return AStar.forObject(target, ship, space);
    }

    public Ship getShip() {
        return ship;
    }

    public ShipRole getRole() {
        return role;
    }

    public AbstractObject getTarget() {
        return target;
    }

    public double getEnergyChange() {
        double oldMin = 0;
        double oldMax = MovementUtil.maxDistance(space);
        double newMin = 0;
        double newMax = 3000;
        double input = space.findShortestDistance(ship.getPosition(), target.getPosition());
        return MovementUtil.linearNormalize(oldMin, oldMax, newMin, newMax, input);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GoToAction)) return false;
        GoToAction that = (GoToAction) o;
        return Objects.equals(ship, that.ship) &&
                role == that.role &&
                Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ship, role, target);
    }
}
