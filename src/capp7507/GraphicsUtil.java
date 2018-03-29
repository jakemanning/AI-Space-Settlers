package capp7507;

import spacesettlers.graphics.CircleGraphics;
import spacesettlers.graphics.SpacewarGraphics;
import spacesettlers.graphics.TargetGraphics;
import spacesettlers.utilities.Position;

import java.awt.*;
import java.util.*;

public class GraphicsUtil {
    private static final int CIRCLE_RADIUS = 2;
    private static final int TARGET_RADIUS = 10;
    private final boolean DEBUG;
    private Set<SpacewarGraphics> graphics;
    private Map<UUID, SpacewarGraphics> targetGraphics;
    private Map<UUID, SpacewarGraphics> obstacleGraphics;

    GraphicsUtil(boolean debug) {
        this.DEBUG = debug;
        graphics = new HashSet<>();
        targetGraphics = new HashMap<>();
        obstacleGraphics = new HashMap<>();
    }

    void loadGraphicsFor(UUID uuid) {
        SpacewarGraphics targetGraphic = targetGraphics.get(uuid);
        SpacewarGraphics obstacleGraphic = obstacleGraphics.get(uuid);
        if(targetGraphic != null) graphics.add(targetGraphic);
        if(obstacleGraphic != null) graphics.add(obstacleGraphic);
    }

    public HashSet<SpacewarGraphics> getGraphics() {
        HashSet<SpacewarGraphics> newGraphics = new HashSet<>(graphics);
        graphics.clear();
        if(DEBUG) {
            return newGraphics;
        } else {
            return new HashSet<>();
        }
    }

    // region AnyGraphics
    void addGraphic(SpacewarGraphics graphic) {
        graphics.add(graphic);
    }


    void addTargetGraphic(UUID uuid, SpacewarGraphics graphics) {
        targetGraphics.put(uuid, graphics);
    }

    void addObstacleGraphic(UUID uuid, SpacewarGraphics graphics) {
        obstacleGraphics.put(uuid, graphics);
    }

    void removeObstacle(UUID uuid) {
        obstacleGraphics.remove(uuid);
    }
    // endregion

    //region Preset
    void addGraphicPreset(Preset preset, Position position) {
        graphics.add(getPreset(preset, position));
    }

    void addTargetPreset(UUID uuid, Preset preset, Position position) {
        targetGraphics.put(uuid, getPreset(preset, position));
    }

    void addObstaclePreset(UUID uuid, Preset preset, Position position) {
        obstacleGraphics.put(uuid, getPreset(preset, position));
    }

    private SpacewarGraphics getPreset(Preset preset, Position position) {
        if(preset == Preset.TARGET) {
            return targetGraphic(position);
        } else if (preset == Preset.YELLOW_CIRCLE) {
            return yellowCircleGraphic(position);
        } else {
            return redCircleGraphic(position);
        }
    }

    private TargetGraphics targetGraphic(Position position) {
        return new TargetGraphics(TARGET_RADIUS, position);
    }

    private CircleGraphics yellowCircleGraphic(Position position) {
        return new CircleGraphics(CIRCLE_RADIUS, Color.YELLOW, position);
    }

    private CircleGraphics redCircleGraphic(Position position) {
        return new CircleGraphics(CIRCLE_RADIUS, Color.RED, position);
    }

    public enum Preset {
        TARGET, YELLOW_CIRCLE, RED_CIRCLE
    }
    // endregion
}
