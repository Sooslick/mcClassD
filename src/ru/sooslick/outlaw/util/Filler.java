package ru.sooslick.outlaw.util;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.sooslick.outlaw.Cfg;

/**
 * Utility builder for filling area with certain type of blocks
 */
@SuppressWarnings("unused")
public class Filler {
    private static final String FILL_SIZE_EXCEED = "fill operation cancelled, blocks limit exceed. Actual size: ";
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

    public Filler() {
    }

    public Filler(World world, Material m) {
        this.world = world;
        this.material = m;
    }

    /**
     * Set world where filler will operate
     *
     * @param world required world
     * @return filler instance
     */
    public Filler setWorld(World world) {
        this.world = world;
        return this;
    }

    /**
     * Set starting X coordinate
     *
     * @param value starting X coordinate
     * @return filler instance
     */
    public Filler setStartX(int value) {
        startX = value;
        return this;
    }

    /**
     * Set starting Y coordinate
     *
     * @param value starting Y coordinate
     * @return filler instance
     */
    public Filler setStartY(int value) {
        startY = value;
        return this;
    }

    /**
     * Set starting Z coordinate
     *
     * @param value starting Z coordinate
     * @return filler instance
     */
    public Filler setStartZ(int value) {
        startZ = value;
        return this;
    }

    /**
     * Set ending X coordinate
     *
     * @param value ending X coordinate
     * @return filler instance
     */
    public Filler setEndX(int value) {
        endX = value;
        return this;
    }

    /**
     * Set ending Y coordinate
     *
     * @param value ending Y coordinate
     * @return filler instance
     */
    public Filler setEndY(int value) {
        endY = value;
        return this;
    }

    /**
     * Set ending Z coordinate
     *
     * @param value ending Z coordinate
     * @return filler instance
     */
    public Filler setEndZ(int value) {
        endZ = value;
        return this;
    }

    /**
     * Set block type
     *
     * @param value block type
     * @return filler instance
     */
    public Filler setMaterial(Material value) {
        material = value;
        return this;
    }

    /**
     * Return block count involved in fill operation
     *
     * @return amount of blocks to fill
     */
    public int size() {
        return (Math.abs(endX - startX) + 1) * (Math.abs(endY - startY) + 1) * (Math.abs(endZ - startZ) + 1);
    }

    /**
     * Fill the area by specified coordinates (both boundaries included)
     *
     * @return true if filled successfully, false otherwise
     */
    @SuppressWarnings("UnusedReturnValue")
    public boolean fill() {
        // validate world
        if (world == null) {
            LoggerUtil.warn(WORLD_NOT_SPECIFIED);
            return false;
        }
        //validate size
        int volume = size();
        if (volume > Cfg.blocksPerSecondLimit) {
            LoggerUtil.warn(FILL_SIZE_EXCEED + volume);
            return false;
        }
        //validate material
        if (material == null) {
            material = Material.AIR;
        }
        //proceed
        int x1 = startX;
        while (x1 <= endX) {
            int x2 = WorldUtil.calcChunk(x1) * 16 + 15;
            if (x2 > endX)
                x2 = endX;
            int z1 = startZ;
            while (z1 <= endZ) {
                int z2 = WorldUtil.calcChunk(z1) * 16 + 15;
                if (z2 > endZ)
                    z2 = endZ;
                fillPart(x1, x2, z1, z2);
                z1 = z2 + 1;
            }
            x1 = x2 + 1;
        }
        LoggerUtil.debug(String.format(FILL_SUCCESS, startX, startY, startZ, endX, endY, endZ));
        return true;
    }

    /**
     * clone
     *
     * @return copy of current filler
     */
    public Filler copy() {
        return new Filler(world, material)
                .setStartX(startX).setEndX(endX)
                .setStartY(startY).setEndY(endY)
                .setStartZ(startZ).setEndZ(endZ);
    }

    public World getWorld() {
        return world;
    }

    public int getStartX() {
        return startX;
    }

    public int getStartY() {
        return startY;
    }

    public int getStartZ() {
        return startZ;
    }

    public int getEndX() {
        return endX;
    }

    public int getEndY() {
        return endY;
    }

    public int getEndZ() {
        return endZ;
    }

    public Material getMaterial() {
        return material;
    }

    private void fillPart(int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = startY; y <= endY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material m = b.getType();
                    if (m == Material.CHEST || m == Material.SPAWNER || m == Material.BELL)
                        b.breakNaturally();
                    b.setType(material);
                }
    }
}
