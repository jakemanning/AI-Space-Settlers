package capp7507;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;
import spacesettlers.objects.AbstractActionableObject;
import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.weapons.AbstractWeapon;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Subclass of PowerupUtil for use in collecting training data for the decision tree learning algorithm
 */
public class TrainingPowerupUtil extends PowerupUtil {
    private static final String KNOWLEDGE_FILE = "capp7507/shooting_data.xml.gz";
    public static final int MAX_SHOOT_DISTANCE = 200;
    private ShotCollection shotAttempts = new ShotCollection();
    private final XStream xStream;
    private Map<UUID, Boolean> missilesShotThisTurn = new HashMap<>();

    public TrainingPowerupUtil(JakeTeamClient client, Random random) {
        super(client, random);
        xStream = new XStream();
        xStream.alias("trainingData", ShotCollection.class);
        loadKnowledge();
    }

    @Override
    Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
        Map<UUID, SpaceSettlersPowerupEnum> powerups = super.getPowerups(space, actionableObjects);
        updateShotAttempts(space);
        return powerups;
    }

    /**
     * Don't worry about energy during training - we want to shoot things, not win
     *
     * @param ship The ship that may need more energy
     * @return False
     */
    @Override
    boolean shipNeedsEnergy(Ship ship) {
        return false;
    }

    /**
     * Don't shield while training
     *
     * @param space      physics
     * @param actionable ship
     */
    @Override
    void shieldIfNeeded(Toroidal2DPhysics space, AbstractActionableObject actionable) {
    }

    /**
     * Update our knowledge about our shot attempts and the missiles in the space
     *
     * @param space physics
     */
    private void updateShotAttempts(Toroidal2DPhysics space) {
        for (AbstractWeapon weapon : space.getWeapons()) {
            if (missilesShotThisTurn.get(weapon.getId()) == null) {
                missilesShotThisTurn.put(weapon.getId(), Boolean.TRUE);
            } else {
                missilesShotThisTurn.put(weapon.getId(), Boolean.FALSE);
            }
        }
        List<UUID> removedMissiles = new ArrayList<>();
        for (UUID missileId : missilesShotThisTurn.keySet()) {
            if (space.getObjectById(missileId) == null) {
                removedMissiles.add(missileId);
            }
        }
        removedMissiles.forEach(uuid -> missilesShotThisTurn.remove(uuid));
        ShotCollection removedShotAttempts = new ShotCollection();
        for (ShotAttempt attempt : shotAttempts) {
            if (attempt.finished()) continue;
            if (attempt.getTurnFired() == space.getCurrentTimestep()) continue;
            if (attempt.missileNotSet()) {
                // set the missile for an attempt by looking for recently shot missiles
                List<AbstractWeapon> thisTurnMissiles = thisTurnMissiles(space);
                if (thisTurnMissiles.size() == 0) {
                    removedShotAttempts.add(attempt);
                } else {
                    attempt.setMissileId(thisTurnMissiles.get(0).getId());
                }
            }
            AbstractObject target = space.getObjectById(attempt.getTargetId());
            AbstractObject missile = space.getObjectById(attempt.getMissileId());
            if (missile == null || !missile.isAlive() || target == null || !target.isAlive()) {
                attempt.markFinished();
            }
            // check if the missile is close enough to hit the target
            // this is the best way I could find for detecting a collision between two objects when one dies upon impact
            // we check if the missile and target are close and hope they collide soon after
            // for learning purposes this is probably okay
            if (missile != null && target != null && space.findShortestDistance(missile.getPosition(), target.getPosition()) < 20) {
                attempt.markHit();
            }
        }
        shotAttempts.removeAll(removedShotAttempts);
    }

    /**
     * Returns missiles fired in this time step
     *
     * @param space physics
     * @return List of AbstractWeapon objects
     */
    private List<AbstractWeapon> thisTurnMissiles(Toroidal2DPhysics space) {
        return space.getWeapons().stream()
                .filter(missile -> missilesShotThisTurn.get(missile.getId()))
                .collect(Collectors.toList());
    }

    /**
     * Set the missile ID on a shot attempt from the last timestep if the shooter matches
     *
     * @param space    physics
     * @param weaponId ID of the missile fired
     */
    private void setNewWeapon(Toroidal2DPhysics space, UUID weaponId) {
        AbstractWeapon weapon = (AbstractWeapon) space.getObjectById(weaponId);
        for (ShotAttempt attempt : shotAttempts) {
            if (attempt.getTurnFired() == space.getCurrentTimestep() - 1
                    && weapon.getFiringShip().getId().equals(attempt.getShooterId())) {
                attempt.setMissileId(weaponId);
            }
        }
    }

    /**
     * When collecting training data, we want to shoot whenever the target is vaguely in front of the ship
     * and within a relatively large distance
     *
     * @param space           physics
     * @param currentPosition The current position of a ship
     * @param target          The potential target for the ship to shoot
     * @return whether the ship is in a reasonable position to shoot the target
     */
    @Override
    boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition, AbstractObject target) {
        double orientation = currentPosition.getOrientation();
        Vector2D vector = space.findShortestDistanceVector(currentPosition, target.getPosition());
        boolean angle = vector.angleBetween(Vector2D.fromAngle(orientation, 1)) < Math.PI;
        boolean distance = vector.getMagnitude() < MAX_SHOOT_DISTANCE;
        return angle && distance;
    }

    /**
     * Fire a missile according to the probability in the parent method and add a shot attempt representing this attempt
     *
     * @param powerupMap A map from ship IDs to powerup types that is added to when shooting
     * @param space      physics
     * @param ship       The ship that will shoot
     * @param target     The object we're aiming for
     * @return Whether the ship will actually fire a shot
     */
    @Override
    boolean shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        boolean shot = super.shoot(powerupMap, space, ship, target);
        if (shot) {
            shotAttempts.add(ShotAttempt.build(space, ship, target));
        }
        return shot;
    }

    /**
     * Save the loaded shot collection file for storing shot attempts or make a new shot collection
     */
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

    /**
     * Load the file for storing shot attempts
     *
     * @param xStream xStream object
     * @return ShotCollection from the filesystem
     * @throws IOException file issues
     */
    private ShotCollection loadFile(XStream xStream) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(KNOWLEDGE_FILE); GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)) {
            System.out.println("Loaded trainingData from " + KNOWLEDGE_FILE);
            return (ShotCollection) xStream.fromXML(gzipInputStream);
        }
    }

    /**
     * Saves the shot collection to disk
     */
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
