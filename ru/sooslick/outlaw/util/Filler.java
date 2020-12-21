package ru.sooslick.outlaw.util;

import org.bukkit.Material;
import org.bukkit.World;
import ru.sooslick.outlaw.Cfg;

public class Filler {
    private static final String FILL_EMPTY = "fill operation cancelled, empty area";
    private static final String FILL_SIZE_EXCEED = "fill operation cancelled, blocks limit exceed";
    private static final String WORLD_NOT_SPECIFIED = "fill operation cancelled, world not specified";

    private World world;
    private int startX;
    private int startY;
    private int startZ;
    private int endX;
    private int endY;
    private int endZ;
    private Material material;

    public Filler setWorld(World world) {
        this.world = world;
        return this;
    }

    public Filler setStartX(int value) {
        startX = value;
        return this;
    }

    public Filler setStartY(int value) {
        startY = value;
        return this;
    }

    public Filler setStartZ(int value) {
        startZ = value;
        return this;
    }

    public Filler setEndX(int value) {
        endX = value;
        return this;
    }

    public Filler setEndY(int value) {
        endY = value;
        return this;
    }

    public Filler setEndZ(int value) {
        endZ = value;
        return this;
    }

    public Filler setMaterial(Material value) {
        material = value;
        return this;
    }

    public boolean fill() {
        //validate size
        int volume = (endX - startX) * (endY - startY) * (endZ - startZ);
        if (Math.abs(volume) > Cfg.blocksPerSecondLimit) {
            LoggerUtil.warn(FILL_SIZE_EXCEED);
            return false;
        } else if (volume == 0) {
            LoggerUtil.warn(FILL_EMPTY);
            return false;
        }
        //validate material
        if (material == null) {
            material = Material.AIR;
        }
        // validate world
        if (world == null) {
            LoggerUtil.warn(WORLD_NOT_SPECIFIED);
            return false;
        }
        //proceed
        for (int x = startX; x <= endX; x++)
            for (int y = startY; y <= endY; y++)
                for (int z = startZ; z <= endZ; z++)
                    world.getBlockAt(x, y, z).setType(material);
        return true;
    }
}
