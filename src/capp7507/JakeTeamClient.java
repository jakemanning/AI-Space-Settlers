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
 * The agents move towards things with the "best value" and shoots when it gets close to enemy ships.
 * If it's low on energy then it goes to the nearest beacon or friendly base.
 * @author Bryan Capps
 *
 */
public class JakeTeamClient extends TeamClient {
	private HashSet<SpacewarGraphics> graphics;
	private AbstractObject target;

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
				AbstractObject target = bestValue(space, ship, space.getAllObjects());
				Position targetPosition = target.getPosition();
				Vector2D targetVelocity = targetPosition.getTranslationalVelocity();
				if (!(target instanceof Ship)) {
					Vector2D toTarget = space.findShortestDistanceVector(currentPosition, targetPosition);
					double angle = toTarget.getAngle();
					targetVelocity = Vector2D.fromAngle(angle, 30);
				}
				MoveAction action = new MoveAction(space, currentPosition, targetPosition, targetVelocity);
				actions.put(ship.getId(), action);
			} else if (actionable instanceof Base) {
				Base base = (Base) actionable;
				this.target = base;
				actions.put(base.getId(), new DoNothingAction());
			}
		}

		return actions;
	}

	private boolean shipNeedsEnergy(Ship ship) {
		return ship.getEnergy() < ship.getMaxEnergy() * 0.2;
	}

	private AbstractObject bestValue(Toroidal2DPhysics space, Ship ship,
												   Collection<AbstractObject> objects) {
		AbstractObject best = null;
		double maximum = Double.MIN_VALUE;
		for (AbstractObject object : objects) {
			double rawDistance = space.findShortestDistance(ship.getPosition(), object.getPosition());
			double scaledDistance = Math.log1p(rawDistance);
			double value = 0;
			if (object instanceof Asteroid) {
				Asteroid asteroid = (Asteroid) object;
				if (asteroid.isMineable()) {
					value = Math.log1p(asteroid.getMass());
				}
			} else if (object instanceof AbstractActionableObject) {
				AbstractActionableObject actionableObject = (AbstractActionableObject) object;
				if (isEnemyTarget(actionableObject)) {
					value = Math.log1p(5 * energyValue(actionableObject));
				} else if (isOurBase(actionableObject)) {
					value = Math.log1p(5 * energyValue(ship) + cargoValue(ship));
				}
			} else if (object instanceof Beacon) {
				value = Math.log1p(5 * energyValue(ship));
			}
			double score = value / scaledDistance;
			if (score > maximum) {
				maximum = score;
				best = object;
			}
		}
		return best;
	}

	private boolean isEnemyTarget(AbstractActionableObject actionableObject) {
		if (actionableObject instanceof Base) {
			Base base = (Base) actionableObject;
			if (base.isHomeBase()) {
				return false;
			}
		}
		return !actionableObject.getTeamName().equals(getTeamName());
	}

	private boolean isOurBase(AbstractActionableObject actionableObject) {
		return actionableObject instanceof Base && actionableObject.getTeamName().equals(getTeamName());
	}

	private double energyValue(AbstractActionableObject ship) {
		double percentEnergy = ship.getEnergy() / ship.getMaxEnergy();
		if (percentEnergy > 0.8) {
			return 10;
		} else if (percentEnergy < 0.3) {
			return 50000;
		} else {
			return percentEnergy * 1000;
		}
	}

	private double cargoValue(Ship ship) {
		final int total = ship.getResources().getTotal();
		return total;
	}

	private Set<AbstractObject> getEnemyShips(Toroidal2DPhysics space, String teamName) {
		Set<AbstractObject> enemies = new HashSet<>();
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
				Collection<AbstractObject> enemyShips = getEnemyShips(space, getTeamName());
				AbstractObject closestEnemyShip = closest(space, ship.getPosition(), enemyShips);
				if (inPositionToShoot(space, ship.getPosition(), closestEnemyShip) && !shipNeedsEnergy(ship)) {
					shoot(powerupMap, ship);
				}
			}
		}
		return powerupMap;
	}

	private void shoot(HashMap<UUID, SpaceSettlersPowerupEnum> powerupMap, Ship ship) {
		if (random.nextDouble() < 0.3) {
			powerupMap.put(ship.getId(), SpaceSettlersPowerupEnum.FIRE_MISSILE);
		}
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

	private boolean inPositionToShoot(Toroidal2DPhysics space, Position currentPosition,
									  AbstractObject target) {
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
