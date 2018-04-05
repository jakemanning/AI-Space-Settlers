package capp7507;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.weapons.AbstractWeapon;
import spacesettlers.objects.weapons.Missile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class TrainingPowerupUtil extends PowerupUtil {
    private static final String KNOWLEDGE_FILE = "capp7507/shooting_data.xml.gz";
    public static final int MAX_SHOOT_DISTANCE = 200;
    private ShotCollection shotAttempts = new ShotCollection();
    private String teamName;
    private final XStream xStream;

    public TrainingPowerupUtil(JakeTeamClient client, Random random) {
        super(client, random);
        teamName = client.getTeamName();
        xStream = new XStream();
        xStream.alias("trainingData", ShotCollection.class);
        loadKnowledge();
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
        for (ShotAttempt attempt : shotAttempts) {
            Missile missile = (Missile) space.getObjectById(attempt.getMissileId());
            AbstractObject target = space.getObjectById(attempt.getTargetId());
            if ((missile == null && !attempt.targetHit()) || target == null) {
                attempt.markMissed();
            } else if (missile != null &&
                    space.findShortestDistance(missile.getPosition(), target.getPosition()) < target.getRadius()) {
                attempt.markHit();
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
        boolean distance = vector.getMagnitude() < MAX_SHOOT_DISTANCE;
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

    private void loadKnowledge() {
        // try to load the population from the existing saved file.  If that fails, start from scratch
        try {
            shotAttempts = loadFile(xStream);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it other than a null pointer because
            // the error will happen the first time you run
            System.out.println("No existing population found - starting a new one from scratch");
            shotAttempts = new ShotCollection();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private ShotCollection loadFile(XStream xStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(KNOWLEDGE_FILE); GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            System.out.println("Loaded trainingData from " + KNOWLEDGE_FILE);
            return (ShotCollection) xStream.fromXML(gzipInputStream);
        }
    }

    @Override
    public void shutDown() {
        try {
            createFile(xStream, shotAttempts);
        } catch (XStreamException | FileNotFoundException e) {
            // if you get an error, handle it somehow as it means your knowledge didn't save
            System.out.println("Can't save knowledge file in shutdown");
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createFile(XStream xStream, ShotCollection trainingPowerupUtil) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(KNOWLEDGE_FILE); GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            xStream.toXML(trainingPowerupUtil, gzipOutputStream);
            System.out.println("Saved training data to " + KNOWLEDGE_FILE);
        }
    }
}
