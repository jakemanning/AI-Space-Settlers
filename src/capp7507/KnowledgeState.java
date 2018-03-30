package capp7507;

import java.io.Serializable;
import java.util.Objects;

public class KnowledgeState implements Serializable {
    public double collisionAvoidanceAngle;
    public int collisionAvoidanceDistanceFactor;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KnowledgeState)) return false;
        KnowledgeState that = (KnowledgeState) o;
        return Double.compare(that.collisionAvoidanceAngle, collisionAvoidanceAngle) == 0 &&
                collisionAvoidanceDistanceFactor == that.collisionAvoidanceDistanceFactor;
    }

    @Override
    public int hashCode() {
        return Objects.hash(collisionAvoidanceAngle, collisionAvoidanceDistanceFactor);
    }
}
