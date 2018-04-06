package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Vector2D;

import java.util.UUID;

/**
 * Stores data about an attempt to shoot another ship.
 * Used in collecting training data for the decision tree learning algorithm.
 */
public class ShotAttempt {
    private UUID shooterId;
    private UUID missileId;
    private UUID targetId;
    private double angle;
    private double distance;
    private double targetSpeed;
    private int turnFired;
    private boolean shotHitTarget;
    private boolean missileGone;

    public ShotAttempt(UUID shooterId, UUID targetId, double angle, double distance, double targetSpeed, int turnFired) {
        this.shooterId = shooterId;
        this.targetId = targetId;
        this.angle = angle;
        this.distance = distance;
        this.targetSpeed = targetSpeed;
        this.turnFired = turnFired;
        shotHitTarget = false;
        missileGone = false;
    }

    public static ShotAttempt build(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        UUID targetId = target.getId();
        double targetSpeed = target.getPosition().getTranslationalVelocity().getMagnitude();
        double orientation = ship.getPosition().getOrientation();
        Vector2D targetVector = space.findShortestDistanceVector(ship.getPosition(), target.getPosition());
        double angle = Math.abs(Vector2D.fromAngle(orientation, 1).angleBetween(targetVector));
        double distance = targetVector.getMagnitude();
        int turnFired = space.getCurrentTimestep();
        return new ShotAttempt(ship.getId(), targetId, angle, distance, targetSpeed, turnFired);
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

    public boolean targetHit() {
        return shotHitTarget;
    }

    public void markFinished() {
        missileGone = true;
    }

    public UUID getShooterId() {
        return shooterId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void markHit() {
        markFinished();
        shotHitTarget = true;
    }

    public boolean finished() {
        return missileGone;
    }

    public boolean missileNotSet() {
        return missileId == null;
    }
}
