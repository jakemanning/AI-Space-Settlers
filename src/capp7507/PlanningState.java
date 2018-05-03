package capp7507;

import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlanningState {
    private final Toroidal2DPhysics space;
    private final int flagScore;
    private final int shipCount;
    private final int closeBases;
    private final int resourcesAvailable;
    private final Map<UUID, Integer> shipEnergies;
    private final Map<UUID, Integer> resourcesAboard;
    private final Map<UUID, ShipRole> roles;
    private final String teamName;

    public PlanningState(Toroidal2DPhysics space, int flagScore, int shipCount, int closeBases,
                         int resourcesAvailable, Map<UUID, Integer> shipEnergies, Map<UUID, Integer> resourcesAboard,
                         Map<UUID, ShipRole> roles) {
        this.space = space;
        this.flagScore = flagScore;
        this.shipCount = shipCount;
        this.closeBases = closeBases;
        this.resourcesAvailable = resourcesAvailable;
        this.shipEnergies = shipEnergies;
        this.resourcesAboard = resourcesAboard;
        this.roles = roles;

        Ship ship = (Ship) space.getObjectById(shipEnergies.keySet().iterator().next());
        this.teamName = ship.getTeamName();
    }

    public Toroidal2DPhysics getSpace() {
        return space;
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

    public String getTeamName() {
        return teamName;
    }

    public PlanningState copy() {
        int flagScore = this.flagScore;
        int shipCount = this.shipCount;
        int closeBases = this.closeBases;
        int resourcesAvailable = this.resourcesAvailable;
        Map<UUID, Integer> shipEnergies = new HashMap<>(this.shipEnergies);
        Map<UUID, Integer> resourcesAboard = new HashMap<>(this.resourcesAboard);
        Map<UUID, ShipRole> roles = new HashMap<>(this.roles);
        return new PlanningState(space, flagScore, shipCount, closeBases, resourcesAvailable, shipEnergies,
                resourcesAboard, roles);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanningState)) return false;
        PlanningState that = (PlanningState) o;
        return flagScore == that.flagScore &&
                shipCount == that.shipCount &&
                closeBases == that.closeBases &&
                resourcesAvailable == that.resourcesAvailable &&
                Objects.equals(shipEnergies, that.shipEnergies) &&
                Objects.equals(resourcesAboard, that.resourcesAboard) &&
                Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {

        return Objects.hash(flagScore, shipCount, closeBases, resourcesAvailable, shipEnergies, resourcesAboard, roles);
    }

    public static class Builder {
        private Toroidal2DPhysics space;
        private int flagScore;
        private int shipCount;
        private int closeBases;
        private int resourcesAvailable;
        private Map<UUID, Integer> shipEnergies;
        private Map<UUID, Integer> resourcesAboard;
        private Map<UUID, ShipRole> roles;
        private String teamName;

        public Builder(PlanningState prevState) {
            this.space = prevState.space;
            this.flagScore = prevState.flagScore;
            this.shipCount = prevState.shipCount;
            this.closeBases = prevState.closeBases;
            this.resourcesAvailable = prevState.resourcesAvailable;
            this.shipEnergies = new HashMap<>(prevState.shipEnergies);
            this.resourcesAboard = new HashMap<>(prevState.resourcesAboard);
            this.roles = new HashMap<>(prevState.roles);
            this.teamName = prevState.teamName;
        }

        public void setSpace(Toroidal2DPhysics space) {
            this.space = space;
        }

        public void setFlagScore(int flagScore) {
            this.flagScore = flagScore;
        }

        public void setShipCount(int shipCount) {
            this.shipCount = shipCount;
        }

        public void setCloseBases(int closeBases) {
            this.closeBases = closeBases;
        }

        public void setResourcesAvailable(int resourcesAvailable) {
            this.resourcesAvailable = resourcesAvailable;
        }

        public void setShipEnergy(UUID shipId, int energy) {
            shipEnergies.put(shipId, energy);
        }

        public void setResourcesAboard(UUID shipId, int resourcesAboard) {
            this.resourcesAboard.put(shipId, resourcesAboard);
        }

        public void setRole(UUID shipId, ShipRole role) {
            roles.put(shipId, role);
        }

        public void setTeamName(String teamName) {
            this.teamName = teamName;
        }

        public PlanningState build() {
            return new PlanningState(space, flagScore, shipCount, closeBases, resourcesAvailable, shipEnergies,
                    resourcesAboard, roles);
        }
    }
}
