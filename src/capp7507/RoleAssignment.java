package capp7507;

import java.util.Objects;
import java.util.UUID;

public class RoleAssignment {
    private UUID shipId;
    private ShipRole role;

    public RoleAssignment(UUID shipId, ShipRole role) {
        this.shipId = shipId;
        this.role = role;
    }

    public UUID getShipId() {
        return shipId;
    }

    public ShipRole getRole() {
        return role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleAssignment)) return false;
        RoleAssignment that = (RoleAssignment) o;
        return Objects.equals(shipId, that.shipId) &&
                role == that.role;
    }

    @Override
    public int hashCode() {

        return Objects.hash(shipId, role);
    }
}
