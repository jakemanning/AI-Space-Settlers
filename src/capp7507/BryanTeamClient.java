package capp7507;

import spacesettlers.actions.*;
import spacesettlers.clients.TeamClient;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
import spacesettlers.objects.resources.ResourcePile;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;
import spacesettlers.utilities.Vector2D;

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
	private HashSet<SpacewarGraphics> graphics;
	private AbstractObject target;
	private boolean avoiding = false;

	@Override
	public void initialize(Toroidal2DPhysics space) {
		graphics = new HashSet<>();
	}

	@Override
	public void shutDown(Toroidal2DPhysics space) {
	}

	@Override
	public Map<UUID, AbstractAction> getMovementStart(Toroidal2DPhysics space,
													  Set<AbstractActionableObject> actionableObjects) {
		HashMap<UUID, AbstractAction> actions = new HashMap<>();

		for (AbstractActionableObject actionable :  actionableObjects) {
			Position currentPosition = actionable.getPosition();

			if (actionable instanceof Ship) {
				Ship ship = (Ship) actionable;
				Collection<AbstractObject> targets = new HashSet<>();
				if (goingToCrash(space, ship)) {
					AbstractAction action = avoidCrashAction(space, currentPosition);
					avoiding = true;
					actions.put(ship.getId(), action);
					return actions;
				} else if (shipNeedsEnergy(ship)) {
					targets.addAll(getEnergySources(space, getTeamName()));
				} else if (ship.getResources().getTotal() > 1000) {
					System.out.println(ship.getResources());
					targets.addAll(getTeamBases(space, getTeamName()));
				} else {
					targets.addAll(getEnemyShips(space, getTeamName()));
					Set<Asteroid> asteroids = getMineableAsteroids(space);
					targets.addAll(asteroids);
				}
				avoiding = false;
				AbstractObject target = closest(space, currentPosition, targets);
				MoveAction action = getMoveAction(space, currentPosition, target);
				this.target = target;
				actions.put(ship.getId(), action);
			} else if (actionable instanceof Base) {
				Base base = (Base) actionable;
				this.target = base;
				actions.put(base.getId(), new DoNothingAction());
			}
		}

		return actions;
	}

	private boolean goingToCrash(Toroidal2DPhysics space, Ship ship) {
		Position shipPosition = ship.getPosition();
		Vector2D shipVelocity = shipPosition.getTranslationalVelocity();
		Set<AbstractActionableObject> ships = getOtherShips(space, ship.getId());
		Set<Asteroid> asteroids = getNonMineableAsteroids(space);
		Set<Base> bases = space.getBases();
		Set<AbstractObject> obstacles = new HashSet<>();
		obstacles.addAll(ships);
		obstacles.addAll(asteroids);
		obstacles.addAll(bases);
		for (AbstractObject obstacle : obstacles) {
			if (obstacle == target) continue;
			Vector2D obstacleVector = space.findShortestDistanceVector(shipPosition, obstacle.getPosition());
			double angleDifference = Math.abs(obstacleVector.getAngle() - shipVelocity.getAngle());
			boolean onCourse = angleDifference < Math.PI / 8;
			if (onCourse) {
				double distance = obstacleVector.getMagnitude();
				double stoppingDistance = shipVelocity.getMagnitude();
				if (distance < stoppingDistance * 3) {
					return true;
				}
			}
		}
		return false;
	}

	private AbstractAction avoidCrashAction(Toroidal2DPhysics space, Position currentPosition) {
		if (avoiding) {
			return new DoNothingAction();
		}

		Vector2D currentVelocity = currentPosition.getTranslationalVelocity();
		double angleDiff = 2 * Math.PI / 3;
		double newAngle = currentVelocity.getAngle() + angleDiff;
		if (target != null) {
			Vector2D targetVector = space.findShortestDistanceVector(currentPosition, target.getPosition());
			double targetAngle = targetVector.getAngle();
			double currentAngle = currentVelocity.getAngle();
			if (targetAngle - currentAngle < 0) {
				newAngle = newAngle - 2*angleDiff;
			}
		}
		Vector2D newVel = Vector2D.fromAngle(newAngle, currentVelocity.getMagnitude() * 6);
		Position newTarget = currentPosition.deepCopy();
		newTarget.setTranslationalVelocity(newVel);
		return new MoveAction(space, currentPosition, newTarget);
	}

	private Set<Base> getTeamBases(Toroidal2DPhysics space, String teamName) {
		Set<Base> results = new HashSet<>();
		for (Base base : space.getBases()) {
			if (Objects.equals(base.getTeamName(), teamName)) {
				results.add(base);
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

	private Set<Asteroid> getMineableAsteroids(Toroidal2DPhysics space) {
		Set<Asteroid> results = new HashSet<>();
		for (Asteroid asteroid : space.getAsteroids()) {
			if (asteroid.isMineable()) {
				results.add(asteroid);
			}
		}
		return results;
	}

	private MoveAction getMoveAction(Toroidal2DPhysics space, Position currentPosition, AbstractObject target) {
		Position targetPosition = target.getPosition();
		double targetSpeed = targetPosition.getTranslationalVelocity().getMagnitude();
		Vector2D distanceVector = space.findShortestDistanceVector(currentPosition, targetPosition);
		double magnitude = (distanceVector.getMagnitude() > 30) ? 20 : targetSpeed;
		double angle = distanceVector.getAngle();
		return new MoveAction(space, currentPosition, targetPosition, Vector2D.fromAngle(angle, magnitude));
	}

	private Set<AbstractObject> getEnergySources(Toroidal2DPhysics space, String teamName) {
		Set<AbstractObject> energySources = new HashSet<>();
		energySources.addAll(space.getBeacons());
		Set<Base> ourBases = new HashSet<>();
		for (Base base : space.getBases()) {
			if (Objects.equals(base.getTeamName(), teamName) && base.getHealingEnergy() > 1000) {
				ourBases.add(base);
			}
		}
		energySources.addAll(ourBases);
		return energySources;
	}

	private boolean shipNeedsEnergy(Ship ship) {
		return ship.getEnergy() < ship.getMaxEnergy() * 0.2;
	}

	private <T extends AbstractObject> T closest(Toroidal2DPhysics space, Position currentPosition,
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

	private Set<AbstractActionableObject> getEnemyShips(Toroidal2DPhysics space, String teamName) {
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

	private Set<AbstractActionableObject> getOtherShips(Toroidal2DPhysics space, UUID id) {
		Set<AbstractActionableObject> ships = new HashSet<>();
		for (Ship ship : space.getShips()) {
			if (!Objects.equals(ship.getId(), id)) {
				ships.add(ship);
			}
		}
		return ships;
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

	/**
	 * If there is enough resourcesAvailable, buy a base.  Place it by finding a ship that is sufficiently
	 * far away from the existing bases
	 * Yeah boiiiiiiiii
	 */
	public Map<UUID, PurchaseTypes> getTeamPurchases(Toroidal2DPhysics space,
													 Set<AbstractActionableObject> actionableObjects,
													 ResourcePile resourcesAvailable,
													 PurchaseCosts purchaseCosts) {

		HashMap<UUID, PurchaseTypes> purchases = new HashMap<UUID, PurchaseTypes>();
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
						//System.out.println("Buying a base!!");
						break;
					}
				}
			}
		}

		// see if you can buy EMPs
		if (purchaseCosts.canAfford(PurchaseTypes.POWERUP_EMP_LAUNCHER, resourcesAvailable)) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Ship) {
					Ship ship = (Ship) actionableObject;

					if (!ship.isValidPowerup(PurchaseTypes.POWERUP_EMP_LAUNCHER.getPowerupMap())) {
						purchases.put(ship.getId(), PurchaseTypes.POWERUP_EMP_LAUNCHER);
					}
				}
			}
		}


		// can I buy a ship?
		if (purchaseCosts.canAfford(PurchaseTypes.SHIP, resourcesAvailable) && !bought_base) {
			for (AbstractActionableObject actionableObject : actionableObjects) {
				if (actionableObject instanceof Base) {
					Base base = (Base) actionableObject;

					purchases.put(base.getId(), PurchaseTypes.SHIP);
					break;
				}

			}

		}


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
				Set<AbstractActionableObject> enemyShips = getEnemyShips(space, getTeamName());
				AbstractActionableObject closestEnemyShip = closest(space, ship.getPosition(), enemyShips);
				if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) {
					shoot(powerupMap, ship);
				}
			}
		}
		return powerupMap;
	}

	private void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
		if (random.nextDouble() < 0.35) {
			powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
		}
	}

	private boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition,
									  AbstractActionableObject target) {
		Position targetPosition = target.getPosition();
		boolean close = space.findShortestDistance(currentPosition, targetPosition) < 100;
		if (!close) {
			return false;
		}
		Vector2D targetVector = space.findShortestDistanceVector(currentPosition, targetPosition);
		double targetAngle = targetVector.getAngle();
		double currentAngle = currentPosition.getOrientation();
		double angleDifference = Math.abs(targetAngle - currentAngle);
		return angleDifference < Math.PI / 12;
	}

}
