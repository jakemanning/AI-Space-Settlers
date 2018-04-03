package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Vector2D;

import java.util.UUID;

public class ShotAttempt {
    private UUID missileId;
    private UUID targetId;
    private double angle;
    private double distance;
    private int turnFired;

    public ShotAttempt(UUID targetId, double angle, double distance, int turnFired) {
        this.targetId = targetId;
        this.angle = angle;
        this.distance = distance;
        this.turnFired = turnFired;
    }

    public static ShotAttempt build(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        UUID targetId = target.getId();
        Vector2D targetVector = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
        double angle = targetVector.getAngle();
        double distance = targetVector.getMagnitude();
        int turnFired = space.getCurrentTimestep();
        return new ShotAttempt(targetId, angle, distance, turnFired);
    }

    public void setMissileId(UUID missileId) {
        this.missileId = missileId;
    }

    public UUID getMissileId() {
        return missileId;
    }

    public int getTurnFired() {
        return turnFired;
    }
}
