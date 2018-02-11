package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

import java.awt.*;
import java.time.Instant;
import java.util.*;

/**
 * A team of alright agents
 *
 * The agents move towards the closest enemy ship and shoots when it gets close.
 * If it's low on energy then it goes to the nearest beacon or friendly base.
 * Kinda avoids obstacles, but not very well.
 * @author Bryan Capps
 *
 */
public class BryanTeamClient extends TeamClient {
    private static final double TARGET_SHIP_SPEED = 25;
	private static final double COLLISION_AVOIDANCE_ANGLE = Math.PI / 2;
	private static final int BASE_RETURN_THRESHOLD = 2000;
    protected static final double RANDOM_SHOOT_THRESHOLD = 0.35;
    protected static final double MIN_SHOOT_DISTANCE = 0.35;
	private static final int BASE_MIN_ENERGY_THRESHOLD = 1000;
	private static final double STOPPING_DISTANCE_MULTIPLIER = 2;
	private static final double COLLISION_DETECTION_ANGLE = Math.PI / 2;


    protected HashSet<SpacewarGraphics> graphics;

	// region Boilerplate
	@Override
	public void initialize(Toroidal2DPhysics space) {
		graphics = new HashSet<>();
	}

	@Override
	public void shutDown(Toroidal2DPhysics space) {
	}

    @Override
    public void getMovementEnd(Toroidal2DPhysics space, Set<AbstractActionableObject> actionableObjects) {
    }

    @Override
    public Set<SpacewarGraphics> getGraphics() {
        HashSet<SpacewarGraphics> newGraphics = new HashSet<>(graphics);
        graphics.clear();
        return newGraphics;
    }
    // endregion

	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
													  Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<>();

		for (AbstractActionableObject actionable :  actionableObjects) {
			Position currentPosition = actionable.getPosition();

			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				Collection<AbstractObject> targets = new HashSet<>();
				if (shipNeedsEnergy(ship)) {
					targets.addAll(getEnergySources(space, getTeamName()));
				} else if (shipShouldDumpResources(ship) || space.getCurrentTimestep() > space.getMaxTime() * 0.99) {
					targets.addAll(getTeamBases(space, getTeamName()));
				} else {
					targets.addAll(getAsteroidsAndEnemies(space));
				}
				AbstractObject closestTarget = closest(space, currentPosition, targets);
				Set<AbstractObject> obstructions = getObstructions(space, ship);
				AbstractObject obstruction = obstructionInPath(space, currentPosition, closestTarget.getPosition(),  obstructions, ship.getRadius());

				// Avoiding targets is an expensive action, only do so if have enough energy
				if (obstruction != null && !shipNeedsEnergy(ship)) {
					AbstractAction action = avoidCrashAction(space, currentPosition, obstruction, ship);
					actions.put(ship.getId(), action);
					return actions;
				}
				MoveAction closeAction = getMoveAction(space, currentPosition, closestTarget, Color.RED);
				actions.put(ship.getId(), closeAction);
			} else if (actionable instanceof Base) {
				Base base = (Base) actionable;
				actions.put(base.getId(), new DoNothingAction());
			}
		}

		return actions;
	}

	protected <T extends AbstractObject> T closest(Toroidal2DPhysics space, Position currentPosition,
												   Collection<T> objects) {
		T closest = null;
		double minimum = Double.MAX_VALUE;
		for (T object : objects) {
			double distance = space.findShortestDistance(currentPosition, object.getPosition());
			if (object instanceof Asteroid) {
				Asteroid asteroid = (Asteroid) object;
				distance = distance / asteroid.getMass();
			}
			if (distance < minimum) {
				minimum = distance;
				closest = object;
			}
		}
		return closest;
	}

	// region Obstacle Avoidance
    protected Set<AbstractObject> getObstructions(Toroidal2DPhysics space, Ship ship) {
        Set<AbstractActionableObject> enemies = getEnemyTargets(space, getTeamName());
        Set<Asteroid> asteroids = getNonMineableAsteroids(space);
        Set<Ship> friendlyShips = getFriendlyShips(space, ship);
        Set<AbstractObject> obstacles = new HashSet<>();
        obstacles.addAll(enemies);
        obstacles.addAll(asteroids);
        return obstacles;
    }

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
    protected AbstractObject obstructionInPath(Toroidal2DPhysics space, Position startPosition, Position goalPosition, Set<AbstractObject> obstructions, int freeRadius) {
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
            // If the distance to the obstruction is greater than the distance to the end goal, ignore the obstruction
            pathToObstruction = space.findShortestDistanceVector(startPosition, obstruction.getPosition());
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

        return closestObstacle;
    }

	private AbstractAction avoidCrashAction(Toroidal2DPhysics space, Position currentPosition,
											AbstractObject obstacle, Ship ship) {
		Vector2D currentVector = new Vector2D(currentPosition);
		Vector2D obstacleVector = space.findShortestDistanceVector(currentPosition, obstacle.getPosition());
		double newAngle = obstacleVector.getAngle() + COLLISION_AVOIDANCE_ANGLE;
		Vector2D avoidanceVector = Vector2D.fromAngle(newAngle, obstacle.getRadius() + ship.getRadius()); // A smaller angle works much better
		Vector2D newTargetVector = currentVector.add(avoidanceVector);
		Position newTarget = new Position(newTargetVector);
		System.out.println("Avoiding a crash  " + Instant.now().getNano());
		graphics.add(new CircleGraphics(2, Color.YELLOW, obstacle.getPosition()));
		Vector2D distanceVector = space.findShortestDistanceVector(currentPosition, newTarget);
		distanceVector = distanceVector.multiply(3);
		return new MoveAction(space, currentPosition, newTarget, distanceVector);
	}
	// endregion

    private MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, AbstractObject target, Color color) {
        Position targetPosition = target.getPosition();
        Vector2D targetVelocity = targetPosition.getTranslationalVelocity();
        Position adjustedTargetPosition = interceptPosition(targetPosition, currentPosition);
        double goalAngle = space.findShortestDistanceVector(currentPosition, adjustedTargetPosition).getAngle();
        Vector2D goalVelocity = Vector2D.fromAngle(goalAngle, TARGET_SHIP_SPEED);
        graphics.add(new CircleGraphics(2, color, adjustedTargetPosition));
        graphics.add(new CircleGraphics(2, color, targetPosition));
        return new MoveAction(space, currentPosition, adjustedTargetPosition, goalVelocity);
    }

	/**
	 * Figure out where the moving target and the cannon will meet when the cannon is fired in that direction
	 * https://stackoverflow.com/questions/2248876/2d-game-fire-at-a-moving-target-by-predicting-intersection-of-projectile-and-u
	 *
	 * @param targetPosition Position of the target at this instant
	 * @param cannonPosition Position of the cannon at this instant
	 * @return Position to aim the cannon in order to collide with the target
	 */
	private Position interceptPosition(Position targetPosition, Position cannonPosition) {
		double targetVelX = targetPosition.getTranslationalVelocityX();
		double targetVelY = targetPosition.getTranslationalVelocityY();
		double targetX = targetPosition.getX();
		double targetY = targetPosition.getY();
		double cannonX = cannonPosition.getX();
		double cannonY = cannonPosition.getY();
		double a = Math.pow(targetVelX, 2) + Math.pow(targetVelY, 2) - Math.pow(TARGET_SHIP_SPEED, 2);
		double b = 2 * (targetVelX * (targetX - cannonX) + targetVelY * (targetY - cannonY));
		double c = Math.pow(targetX - cannonX, 2) + Math.pow(targetY - cannonY, 2);
		double disc = Math.pow(b, 2) - 4 * a * c;
		if (disc < 0) {
			return targetPosition;
		}
		double t1 = (-b + Math.sqrt(disc)) / (2 * a);
		double t2 = (-b - Math.sqrt(disc)) / (2 * a);
		double t;
		if (t1 > 0) {
			if (t2 > 0) t = Math.min(t1, t2);
			else t = t1;
		} else {
			t = t2;
		}
		double aimX = t * targetVelX + targetX;
		double aimY = t * targetVelY + targetY;
		return new Position(aimX, aimY);
	}

	protected boolean shipNeedsEnergy(Ship ship) {
		return ship.getEnergy() < ship.getMaxEnergy() * 0.2;
	}

	// region Objects in Space
    private Set<AbstractObject> getEnergySources(Toroidal2DPhysics space, String teamName) {
        Set<AbstractObject> energySources = new HashSet<>();
        energySources.addAll(space.getBeacons());
        Set<Base> ourBases = new HashSet<>();
        for (Base base : space.getBases()) {
            if (Objects.equals(base.getTeamName(), teamName) && base.getHealingEnergy() > BASE_MIN_ENERGY_THRESHOLD) {
                ourBases.add(base);
            }
        }
        energySources.addAll(ourBases);
        return energySources;
    }

    private boolean shipShouldDumpResources(Ship ship) {
        return ship.getResources().getTotal() > BASE_RETURN_THRESHOLD;
    }

    private Collection<AbstractObject> getAsteroidsAndEnemies(Toroidal2DPhysics space) {
        Collection<AbstractObject> targets = new HashSet<>();
        targets.addAll(getEnemyTargets(space, getTeamName()));
        Set<Asteroid> asteroids = getMineableAsteroids(space);
        targets.addAll(asteroids);
        return targets;
    }

    private Set<Base> getTeamBases(Toroidal2DPhysics space, String teamName) {
        Set<Base> results = new HashSet<>();
        for (Base base : space.getBases()) {
            if (base.getTeamName().equals(teamName)) {
                results.add(base);
            }
        }
        return results;
    }

    private Set<AbstractActionableObject> getEnemyTargets(Toroidal2DPhysics space, String teamName) {
        Set<AbstractActionableObject> enemies = new HashSet<>();
        for (Ship ship : space.getShips()) {
            if (!Objects.equals(ship.getTeamName(), teamName)) {
                enemies.add(ship);
            }
        }
        for (Base base : space.getBases()) {
            if (!base.isHomeBase() && !Objects.equals(base.getTeamName(), teamName)) {
                enemies.add(base);
            }
        }
        return enemies;
    }

    private Set<Asteroid> getMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> results = new HashSet<>();
        for (Asteroid asteroid : space.getAsteroids()) {
            if (asteroid.isMineable()) {
                results.add(asteroid);
            }
        }
        return results;
    }

    private Set<Asteroid> getNonMineableAsteroids(Toroidal2DPhysics space) {
        Set<Asteroid> results = new HashSet<>();
        for (Asteroid asteroid : space.getAsteroids()) {
            if (!asteroid.isMineable()) {
                results.add(asteroid);
            }
        }
        return results;
    }

    private Set<Ship> getFriendlyShips(Toroidal2DPhysics space, Ship ship) {
        Set<Ship> results = new HashSet<>();
        for (Ship otherShip : space.getShips()) {
            if (otherShip.getTeamName().equals(ship.getTeamName()) && !otherShip.getId().equals(ship.getId())) {
                results.add(otherShip); // Should it be otherShip instead of ship?
            }
        }
        return results;
    }

	private Set<AbstractActionableObject> getOtherShips(Toroidal2DPhysics space, UUID id) {
		Set<AbstractActionableObject> ships = new HashSet<>();
		for (Ship ship : space.getShips()) {
			if (!Objects.equals(ship.getId(), id)) {
				ships.add(ship);
			}
		}
		return ships;
	}
    // endregion

	// region Powerups and Purchases
	/**
	 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
	 * far away from the existing bases
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
													 Set<AbstractActionableObject> actionableObjects,
													 ResourcePile resourcesAvailable,
													 PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<>();
		double BASE_BUYING_DISTANCE = 200;
		boolean bought_base = false;

		if (purchaseCosts.canAfford(PurchaseTypes.BASE, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;
					Set<Base> bases = space.getBases();

					// how far away is this ship to a base of my team?
					double maxDistance = Double.MIN_VALUE;
					for (Base base : bases) {
						if (base.getTeamName().equalsIgnoreCase(getTeamName())) {
							double distance = space.findShortestDistance(ship.getPosition(), base.getPosition());
							if (distance > maxDistance) {
								maxDistance = distance;
							}
						}
					}

					if (maxDistance > BASE_BUYING_DISTANCE) {
						purchases.put(ship.getId(), PurchaseTypes.BASE);
						bought_base = true;
						break;
					}
				}
			}
		}

//        // can I purchase double max energy?
//        if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY, resourcesAvailable)) {
//            for (AbstractActionableObject actionableObject : actionableObjects) {
//                if (actionableObject instanceof Ship) {
//                    Ship ship = (Ship) actionableObject;
//
//                    if (!ship.isValidPowerup(PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY.getPowerupMap())) {
//                        purchases.put(ship.getId(), PurchaseTypes.POWERUP_DOUBLE_MAX_ENERGY);
//                    }
//                }
//            }
//        }
//
//        // can I purchase double healing base energy?
//        if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED, resourcesAvailable)) {
//            for (AbstractActionableObject actionableObject : actionableObjects) {
//                if (actionableObject instanceof Base) {
//                    Base base = (Base) actionableObject;
//
//                    if (!base.isValidPowerup(PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED.getPowerupMap())) {
//                        purchases.put(base.getId(), PurchaseTypes.POWERUP_DOUBLE_BASE_HEALING_SPEED);
//                    }
//                }
//            }
//        }

		return purchases;
	}

	/**
	 * This is the new way to shoot (and use any other power up once they exist)
	 */
	public Map<UUID, SpaceSettlersPowerupEnum> getPowerups(Toroidal2DPhysics space,
														   Set<AbstractActionableObject> actionableObjects) {

		HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap = new HashMap<>();

		for (AbstractObject actionable :  actionableObjects) {
			if (actionable instanceof Ship) {
                Ship ship = (Ship) actionable;
                if (inPositionToShoot(space, ship.getPosition()) && !shipNeedsEnergy(ship)) {
                    shoot(powerupMap, ship);
                }
            }
//			} else if (actionable instanceof Base) {
//                Base base = (Base) actionable;
//                powerupMap.put(base.getId(), SpaceSettlersPowerupEnum.DOUBLE_BASE_HEALING_SPEED);
//            }
//            powerupMap.put(actionable.getId(), SpaceSettlersPowerupEnum.DOUBLE_MAX_ENERGY);
		}
		return powerupMap;
	}

    private boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition) {
        Set<AbstractActionableObject> enemyShips = getEnemyTargets(space, getTeamName());
        for (AbstractActionableObject target : enemyShips) {
            Position targetPosition = target.getPosition();
            boolean close = space.findShortestDistance(currentPosition, targetPosition) < MIN_SHOOT_DISTANCE;
            if (!close) {
                continue;
            }
            Vector2D targetVector = space.findShortestDistanceVector(currentPosition, targetPosition);
            double targetAngle = targetVector.getAngle();
            double currentAngle = currentPosition.getOrientation();
            double angleDifference = Math.abs(targetAngle - currentAngle);
            if (angleDifference < Math.PI / 12) {
                return true;
            }
        }
        return false;
    }

	protected void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
		if (random.nextDouble() < RANDOM_SHOOT_THRESHOLD) {
			powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
		}
	}
	// endregion
}
