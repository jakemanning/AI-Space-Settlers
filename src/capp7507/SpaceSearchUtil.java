package capp7507;

import spacesettlers.objects.*;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static capp7507.MovementUtil.interceptPosition;

class SpaceSearchUtil {
    private static final int MAX_OBSTRUCTION_DETECTION = 150;
    private static Position upperFlagPosition;
    private static Position lowerFlagPosition;
    static Position baseLeftHalfPosition;
    static Position baseRightHalfPosition;
    static Boolean targetFlagIsOnLeftSide;

    /**
     * Check to see if following a straight line path between two given locations would result in a collision with a provided set of obstructions
     *
     * @param startPosition the starting location of the straight line path
     * @param goalPosition  the ending location of the straight line path
     * @param obstructions  an Set of AbstractObject obstructions (i.e., if you don't wish to consider mineable asteroids or beacons obstructions)
     * @param freeRadius    used to determine free space buffer size
     * @return The closest obstacle between a start and goal position, if exists
     * @author Andrew and Thibault
     */
    static AbstractObject obstructionInPath(Toroidal2DPhysics space, Position startPosition, Position goalPosition, Set<? extends AbstractObject> obstructions, int freeRadius) {
        Vector2D pathToGoal = space.findShortestDistanceVector(startPosition, goalPosition);    // Shortest straight line path from startPosition to goalPosition
        double distanceToGoal = pathToGoal.getMagnitude();                                        // Distance of straight line path

        AbstractObject closestObstacle = null; // Closest obstacle in the path
        double distanceToObstacle = Double.MAX_VALUE;

        // Calculate distance between obstruction center and path (including buffer for ship movement)
        // Uses hypotenuse * sin(theta) = opposite (on a right hand triangle)
        Vector2D pathToObstruction; // Vector from start position to obstruction
        double angleBetween;        // Angle between vector from start position to obstruction

        // Loop through obstructions
        for (AbstractObject obstruction : obstructions) {
            // Ignore obstruction if is our target
            if (obstruction.getPosition().equalsLocationOnly(goalPosition)) {
                continue;
            }
            // If the distance to the obstruction is greater than the distance to the end goal, ignore the obstruction
            Position interceptPosition = interceptPosition(space, obstruction.getPosition(), startPosition);
            pathToObstruction = space.findShortestDistanceVector(startPosition, interceptPosition);
            if (pathToObstruction.getMagnitude() > distanceToGoal) {
                continue;
            }

            // Ignore angles > 90 degrees
            angleBetween = Math.abs(pathToObstruction.angleBetween(pathToGoal));
            if (angleBetween > Math.PI / 2) {
                continue;
            }

            // Compare distance between obstruction and path with buffer distance
            if (pathToObstruction.getMagnitude() * Math.sin(angleBetween) < obstruction.getRadius() + freeRadius * 1.5) {
                double distance = space.findShortestDistance(startPosition, obstruction.getPosition());
                if (distance < distanceToObstacle) {
                    distanceToObstacle = distance;
                    closestObstacle = obstruction;
                }
            }
        }

        if (closestObstacle == null || space.findShortestDistance(startPosition, closestObstacle.getPosition()) > MAX_OBSTRUCTION_DETECTION) {
            return null; // No obstruction
        } else {
            return closestObstacle;
        }
    }

    /**
     * Get all the objects in the given space that the given ship considers obstructions.
     * This includes ships and bases from other teams, unmineable asteroids, and other ships on the team.
     *
     * @param space physics
     * @param ship  Ship to use as a basis for determining enemy ships and bases
     * @return The set of obstructions
     */
    static Set<AbstractObject> getObstructions(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
        Set<AbstractActionableObject> enemies = getEnemyTargets(space, ship.getTeamName());
        Set<Asteroid> asteroids = getUnmineableAsteroids(space);
        Set<Ship> friendlyShips = getFriendlyShips(space, ship);
        Set<Base> bases = new HashSet<>(space.getBases());
        Set<AbstractObject> obstacles = new HashSet<>();
        obstacles.addAll(enemies);
        obstacles.addAll(asteroids);
        obstacles.addAll(friendlyShips);
        obstacles.addAll(bases);
        if (target != null) {
            obstacles.remove(target);
        }
        return obstacles;
    }

    /**
     * Get all the unmineable asteroids in the space
     *
     * @param space physics
     * @return A set of all the unmineable asteroids
     */
    private static Set<Asteroid> getUnmineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> asteroids = new HashSet<>(space.getAsteroids());
        asteroids.removeAll(getMineableAsteroids(space));
        return asteroids;
    }

    /**
     * Get all the mineable asteroids in the space
     *
     * @param space physics
     * @return A set of all the mineable asteroids
     */
    static Set<Asteroid> getMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> results = new HashSet<>();
        for (Asteroid asteroid : space.getAsteroids()) {
            if (asteroid.isMineable()) {
                results.add(asteroid);
            }
        }
        return results;
    }

    /**
     * Get all the ships and bases that belong to other teams
     *
     * @param space    physics
     * @param teamName The name of the team whose ships and bases are not enemy targets
     * @return all enemies
     */
    static Set<AbstractActionableObject> getEnemyTargets(Toroidal2DPhysics space, String teamName) {
        Set<AbstractActionableObject> enemies = new HashSet<>();
        // get enemy ships
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getTeamName(), teamName)) {
                enemies.add(ship);
            }
        }
        // get enemy bases
        for (Base base : space.getBases()) {
            if (!Objects.equals(base.getTeamName(), teamName)) {
                enemies.add(base);
            }
        }
        return enemies;
    }

    /**
     * Get all the ships that are on the same team as the given ship (minus the given ship)
     *
     * @param space physics
     * @param ship  The ship to use to get the ships on the same team
     * @return A set of all the ships on the same team as the given ship
     */
    private static Set<Ship> getFriendlyShips(Toroidal2DPhysics space, Ship ship) {
        Set<Ship> results = new HashSet<>();
        for (Ship otherShip : space.getShips()) {
            // check that the team names match, but the ship IDs do not
            if (otherShip.getTeamName().equals(ship.getTeamName()) && !otherShip.getId().equals(ship.getId())) {
                results.add(otherShip);
            }
        }
        return results;
    }


    /**
     * Get all the bases that belong to the team with the given team name
     *
     * @param space    physics
     * @param teamName The team name for the bases
     * @return A set of bases that belong to the team with the given team name
     */
    static Set<Base> getTeamBases(Toroidal2DPhysics space, String teamName) {
        Set<Base> results = new HashSet<>();
        for (Base base : space.getBases()) {
            if (base.getTeamName().equals(teamName)) {
                results.add(base);
            }
        }
        return results;
    }

    static Set<AbstractObject> getEnergySources(Toroidal2DPhysics space, String teamName) {
        Set<AbstractObject> sources = new HashSet<>(getTeamBases(space, teamName));
        sources.addAll(space.getBeacons());
        return sources;
    }

    static AbstractObject getUpperFlagPosition(Toroidal2DPhysics space, String teamName) {
        if (upperFlagPosition == null) {
            initFlagPositions(space, teamName);
        }
        return fakeFlagObject(upperFlagPosition);
    }

    static AbstractObject getLowerFlagPosition(Toroidal2DPhysics space, String teamName) {
        if (lowerFlagPosition == null) {
            initFlagPositions(space, teamName);
        }
        return fakeFlagObject(lowerFlagPosition);
    }

    private static AbstractObject fakeFlagObject(final Position position) {
        return new MadeUpObject(position);
    }

    private static void initFlagPositions(Toroidal2DPhysics space, String teamName) {
        Position flagPosition = getTargetFlag(space, teamName).getPosition();
        baseLeftHalfPosition = new Position(space.getWidth() * 0.1, space.getHeight() * 0.5);
        baseRightHalfPosition = new Position(space.getWidth() * 0.9, space.getHeight() * 0.5);
        targetFlagIsOnLeftSide = space.findShortestDistance(baseLeftHalfPosition, flagPosition) < space.findShortestDistance(baseRightHalfPosition, flagPosition);
        if (flagPosition.getY() > space.getHeight() / 2) {
            lowerFlagPosition = flagPosition;
            upperFlagPosition = flagPosition.deepCopy();
            upperFlagPosition.setY(flagPosition.getY() - 550);
        } else {
            upperFlagPosition = flagPosition;
            lowerFlagPosition = flagPosition.deepCopy();
            lowerFlagPosition.setY(flagPosition.getY() + 550);
        }
    }

    static Flag getTargetFlag(Toroidal2DPhysics space, String teamName) {
        return space.getFlags().stream()
                .filter(f -> !f.getTeamName().equals(teamName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Multiple flags belonging to other teams"));
    }

    static Set<Ship> getOurShips(Toroidal2DPhysics space, String teamName) {
        return space.getShips().stream()
                .filter(s -> s.getTeamName().equals(teamName))
                .collect(Collectors.toSet());
    }
}
