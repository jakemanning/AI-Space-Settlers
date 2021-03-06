package capp7507;

import spacesettlers.actions.MoveAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Action that takes a ship's location, angle to move to, and how far to move to
 * Stores obstacle as state as well
 */
class AvoidAction extends MoveAction {
    private AbstractObject obstacle;


    /**
     *
     * @param space
     * @param currentLocation the current location of the ship
     * @param targetLocation the target location of the ship
     * @param targetVelocity the velocity the ship should be at when it reaches its target location
     * @param obstacle
     */
    AvoidAction(Toroidal2DPhysics space, Position currentLocation, Position targetLocation, Vector2D targetVelocity, AbstractObject obstacle) {
        super(space, currentLocation, targetLocation, targetVelocity);
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
    static AvoidAction build(Toroidal2DPhysics space, Position currentLocation, double avoidAngle, double avoidDistance, AbstractObject obstacle) {
        // Instead of MoveAction I should use RawAction so I don't have to deal with PD which leads to strange results

        Vector2D currentVector = new Vector2D(currentLocation);
        Vector2D targetVector = currentVector.add(Vector2D.fromAngle(avoidAngle, avoidDistance));
        Position target = new Position(targetVector);
        Vector2D targetVelocity = Vector2D.fromAngle(avoidAngle, JakeTeamClient.TARGET_SHIP_SPEED);
        return new AvoidAction(space, currentLocation, target, targetVelocity, obstacle);
    }

    AbstractObject getObstacle() {
        return obstacle;
    }
}
