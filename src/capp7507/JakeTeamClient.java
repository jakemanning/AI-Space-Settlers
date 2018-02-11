package capp7507;

import spacesettlers.actions.*;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.objects.*;
import spacesettlers.objects.powerups.SpaceSettlersPowerupEnum;
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
public class JakeTeamClient extends BryanTeamClient {
	private static final int SHIP_ENERGY_WEIGHT = 600;
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

	private AbstractObject bestValue(Toroidal2DPhysics space, Ship ship,
												   Collection<AbstractObject> objects) {
		AbstractObject best = null;
		double maximum = Double.MIN_VALUE;
		for (AbstractObject object : objects) {
			double rawDistance = space.findShortestDistance(ship.getPosition(), object.getPosition());
			double scaledDistance = Math.log1p(rawDistance);
			scaledDistance = scaledDistance + angleValue(space, ship, object);
			double value = 0;
			Set<AbstractObject> obstructions = getObstructions(space, ship);
			if (!space.isPathClearOfObstructions(ship.getPosition(), object.getPosition(), obstructions, ship.getRadius())) {
				continue;
			}
			if (object instanceof Asteroid) {
				Asteroid asteroid = (Asteroid) object;
				if (asteroid.isMineable()) {
					value = asteroid.getMass();
				}
			} else if (object instanceof AbstractActionableObject) {
				AbstractActionableObject actionableObject = (AbstractActionableObject) object;
				if (isEnemyTarget(actionableObject)) {
					value = energyValue(actionableObject);
				} else if (isOurBase(actionableObject)) {
					value = energyValue(ship) + cargoValue(ship);
				} else if (actionableObject.getId() == ship.getId()) {
					continue;
				}
			} else if (object instanceof Beacon) {
				value = energyValue(ship);
			}
			double score = value / scaledDistance;
			if (score > maximum) {
				maximum = score;
				best = object;
			}
		}
		return best;
	}

	private double angleValue(Toroidal2DPhysics space, Ship ship, AbstractObject target) {
		Position currentPosition = ship.getPosition();
		Position targetPosition = target.getPosition();
		Vector2D currentDirection = currentPosition.getTranslationalVelocity();
		double currentAngle = currentDirection.getAngle();
		Vector2D targetDirection = space.findShortestDistanceVector(currentPosition, targetPosition);
		double targetAngle = targetDirection.getAngle();
		return Math.abs(currentAngle - targetAngle);
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
		return SHIP_ENERGY_WEIGHT / (percentEnergy + 0.001 /* add a bit to avoid dividing by zero */);
	}

	private double cargoValue(Ship ship) {
		return ship.getResources().getTotal();
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
