package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.utilities.Position;

/**
 * A 'made up' object that essentially wraps a {@link Position} in an object
 * So we can call {@link AStar} search on our position
 */
public class MadeUpObject extends AbstractObject {
    MadeUpObject(Position position) {
        super(0, 10, position);
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public AbstractObject deepClone() {
        return new MadeUpObject(position);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MadeUpObject) {
            MadeUpObject madeUpObject = (MadeUpObject) obj;
            return position.equalsLocationOnly(madeUpObject.position);
        }
        return false;
    }
}
