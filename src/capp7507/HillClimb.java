package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.objects.Ship;
import spacesettlers.simulator.Toroidal2DPhysics;
import spacesettlers.utilities.Position;

import java.util.List;

public class HillClimb extends Plan {

    public static HillClimb forObject(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        return new HillClimb(goal, ship, space);
    }

    private HillClimb(AbstractObject goal, Ship ship, Toroidal2DPhysics space) {
        super(goal, ship, space);
    }

    /**
     * A* search returns null for failure otherwise a list of positions to travel through
     *
     * @param searchGraph The graph of positions to search through
     *
     */
    @Override
    List<Position> search(Graph<Node> searchGraph) {
        return null;
    }

    @Override
    double heuristicCostEstimate(Position start, Position end) {
        return space.findShortestDistance(start, end);
    }
}
