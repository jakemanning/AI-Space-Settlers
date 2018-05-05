package capp7507;

import spacesettlers.objects.AbstractObject;
import spacesettlers.utilities.Position;

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
