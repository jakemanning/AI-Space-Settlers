package capp7507;

import java.util.Map;
import java.util.UUID;

public class PlanningState {
    private final int flagScore;
    private final int shipCount;
    private final int closeBases;
    private final int resourcesAvailable;
    private final Map<UUID, Integer> shipEnergies;
    private final Map<UUID, Integer> resourcesAboard;
    private final Map<UUID, ShipRole> roles;

    public PlanningState(int flagScore, int shipCount, int closeBases, int resourcesAvailable, Map<UUID, Integer> shipEnergies, Map<UUID, Integer> resourcesAboard, Map<UUID, ShipRole> roles) {
        this.flagScore = flagScore;
        this.shipCount = shipCount;
        this.closeBases = closeBases;
        this.resourcesAvailable = resourcesAvailable;
        this.shipEnergies = shipEnergies;
        this.resourcesAboard = resourcesAboard;
        this.roles = roles;
    }

    public int getFlagScore() {
        return flagScore;
    }

    public int getShipCount() {
        return shipCount;
    }

    public int getCloseBases() {
        return closeBases;
    }

    public int getResourcesAvailable() {
        return resourcesAvailable;
    }

    public Map<UUID, Integer> getShipEnergies() {
        return shipEnergies;
    }

    public Map<UUID, Integer> getResourcesAboard() {
        return resourcesAboard;
    }

    public Map<UUID, ShipRole> getRoles() {
        return roles;
    }
}
