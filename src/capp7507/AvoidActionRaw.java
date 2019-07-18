package capp7507;

import spacesettlers.actions.RawAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Action that takes a ship's location, angle to move to, and how far to move to
 * Stores obstacle as state as well
 */
class AvoidActionRaw extends RawAction {
    private AbstractObject obstacle;

    /**
     *
     * @param space
     * @param currentLocation the current location of the ship
     * @param targetLocation the target location of the ship
     * @param targetVelocity the velocity the ship should be at when it reaches its target location
     * @param obstacle
     */
    AvoidActionRaw(Vector2D targetLocation, double rotAccel, AbstractObject obstacle) {
        super(targetLocation, rotAccel);
        this.obstacle = obstacle;
    }

    /**
     * Helper to construct an avoid action
     * @param space physics
     * @param currentLocation ship's position
     * @param avoidAngle what angle we want the ship to move
     * @param avoidDistance how far it should move
     * @param obstacle which obstacle are we heading to?
     * @return the avoid action with all of this built in.
     */
    static AvoidActionRaw build(Toroidal2DPhysics space, Position currentLocation, double avoidAngle, double avoidDistance, AbstractObject obstacle) {
        // Instead of MoveAction I should use RawAction so I don't have to deal with PD which leads to strange results

        Vector2D currentVector = new Vector2D(currentLocation);
        Vector2D targetVector = Vector2D.fromAngle(avoidAngle, avoidDistance);
        return new AvoidActionRaw(targetVector, 0, obstacle);
    }

    AbstractObject getObstacle() {
        return obstacle;
    }
}
