package capp7507;

import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.weapons.AbstractWeapon;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.*;

public class TrainingPowerupUtil extends PowerupUtil {
    private Set<ShotAttempt> shotAttempts = new HashSet<>();
    private HashMap<UUID, Integer> trackedWeapons = new HashMap<>();
    private String teamName;

    public TrainingPowerupUtil(JakeTeamClient client, Random random) {
        super(client, random);
        teamName = client.getTeamName();
    }

    @Override
    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
        Map<UUID, SpaceSettlersPowerupEnum> powerups = super.getPowerups(space, actionableObjects);
        updateMissiles(space);
        return powerups;
    }

    private void updateMissiles(Toroidal2DPhysics space) {
        Set<AbstractWeapon> weapons = space.getWeapons();
        for (AbstractWeapon weapon : weapons) {
            if (!weapon.getFiringShip().getTeamName().equals(teamName)) continue;
            UUID weaponId = weapon.getId();
            if (!weaponWasShot(weaponId)) {
                setNewWeapon(space, weaponId);
            }
        }
    }

    private void setNewWeapon(Toroidal2DPhysics space, UUID weaponId) {
        for (ShotAttempt attempt : shotAttempts) {
            if (attempt.getTurnFired() == space.getCurrentTimestep() - 1) {
                attempt.setMissileId(weaponId);
            }
        }
    }

    private boolean weaponWasShot(UUID weaponId) {
        for (ShotAttempt attempt : shotAttempts) {
            if (weaponId.equals(attempt.getMissileId())) {
                return true;
            }
        }
        return false;
    }

    @Override
    boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition, AbstractObject target) {
        Vector2D vector = space.findShortestDistanceVector(currentPosition, target.getPosition());
        boolean angle = vector.getAngle() < Math.PI;
        boolean distance = vector.getMagnitude() < 200;
        return angle && distance;
    }

    @Override
    boolean shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        boolean shot = super.shoot(powerupMap, space, ship, target);
        if (shot) {
            shotAttempts.add(ShotAttempt.build(space, ship, target));
        }
        return shot;
    }

    /*
        Duplicate of above, but I'm not sure I'm not gonna mess up
         */
    private void updateWeapons(Toroidal2DPhysics space) {
        Set<AbstractWeapon> weapons = space.getWeapons();
        for (AbstractWeapon weapon : weapons) {
            UUID weaponId = weapon.getId();
            if (!trackedWeapons.containsKey(weaponId)) {
                trackedWeapons.put(weaponId, space.getCurrentTimestep());
            }
        }
        for (UUID weaponId : trackedWeapons.keySet()) {
            if (space.getObjectById(weaponId) == null) {
                trackedWeapons.remove(weaponId);
            }
        }
    }
}
