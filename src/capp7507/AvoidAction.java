package capp7507;

import spacesettlers.actions.MoveAction;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

class AvoidAction extends MoveAction {
    private Position targetLoc;

    AvoidAction(Toroidal2DPhysics space, Position currentLocation, Position targetLocation, Vector2D targetVelocity) {
        super(space, currentLocation, targetLocation, targetVelocity);
        targetLoc = targetLocation;
    }

    public Position getTargetLoc() {
        return targetLoc;
    }
}
