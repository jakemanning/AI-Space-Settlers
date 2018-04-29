package capp7507;

import spacesettlers.actions.MoveAction;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

/**
 * Action that takes a ship's location, angle to move to, and how far to move to
 * Stores obstacle as state as well
 */
class AvoidAction extends MoveAction {

    AvoidAction(Toroidal2DPhysics space, Position currentLocation, Position targetLocation, Vector2D targetVelocity) {
        super(space, currentLocation, targetLocation, targetVelocity);
    }

}
