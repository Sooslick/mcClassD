package ru.sooslick.outlaw.util;

import org.bukkit.Material;
import org.bukkit.World;
import ru.sooslick.outlaw.Cfg;

public class Filler {
    private static final String FILL_SIZE_EXCEED = "fill operation cancelled, blocks limit exceed";
    private static final String FILL_SUCCESS = "Filled area from %s, %s, %s to %s, %s, %s";
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
        // validate world
        if (world == null) {
            LoggerUtil.warn(WORLD_NOT_SPECIFIED);
            return false;
        }
        //validate size
        int volume = (Math.abs(endX - startX) + 1) * (Math.abs(endY - startY) + 1) * (Math.abs(endZ - startZ) + 1);
        if (volume > Cfg.blocksPerSecondLimit) {
            LoggerUtil.warn(FILL_SIZE_EXCEED);
            return false;
        }
        //validate material
        if (material == null) {
            material = Material.AIR;
        }
        //proceed
        for (int x = startX; x <= endX; x++)
            for (int y = startY; y <= endY; y++)
                for (int z = startZ; z <= endZ; z++)
                    world.getBlockAt(x, y, z).setType(material);
        LoggerUtil.debug(String.format(FILL_SUCCESS, startX, startY, startZ, endX, endY, endZ));
        return true;
    }
}
