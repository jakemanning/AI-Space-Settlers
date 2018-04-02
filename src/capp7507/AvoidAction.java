package capp7507;

import spacesettlers.actions.MoveAction;
import spacesettlers.objects.AbstractObject;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

class AvoidAction extends MoveAction {
    private AbstractObject obstacle;

    AvoidAction(Toroidal2DPhysics space, Position currentLocation, Position targetLocation, Vector2D targetVelocity, AbstractObject obstacle) {
        super(space, currentLocation, targetLocation, targetVelocity);
        this.obstacle = obstacle;
    }

    static AvoidAction build(Toroidal2DPhysics space, Position currentLocation, double avoidAngle,
                             double avoidDistance, AbstractObject obstacle) {
        Vector2D currentVector = new Vector2D(currentLocation);
        Vector2D targetVector = currentVector.add(Vector2D.fromAngle(avoidAngle, avoidDistance));
        Position target = new Position(targetVector);
        Vector2D targetVelocity = Vector2D.fromAngle(avoidAngle, JakeTeamClient.TARGET_SHIP_SPEED);
        return new AvoidAction(space, currentLocation, target, targetVelocity, obstacle);
    }

    public AbstractObject getObstacle() {
        return obstacle;
    }
}
