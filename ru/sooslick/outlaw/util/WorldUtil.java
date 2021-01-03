package ru.sooslick.outlaw.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.ChestTracker;
import ru.sooslick.outlaw.Engine;

import java.util.ArrayList;
import java.util.List;

public class WorldUtil {
    private static final double DISTANCE_MAX = 100500d;
    private static final String COMMA = ", ";
    private static final String SAFETIZE = "Created safe location at %s";
    private static final String SAFELOC_FAIL = "getSafeRandomLocation - fail, reason: %s | %s";
    private static final String SAFELOC_FAIL_LIQUID = "liquid";
    private static final String SAFELOC_FAIL_OBSTRUCTION = "obstruction";

    public static final List<Material> DANGERS;
    public static final List<Material> EXCLUDES;

    static {
        DANGERS = new ArrayList<>();
        DANGERS.add(Material.FIRE);
        DANGERS.add(Material.CACTUS);
        DANGERS.add(Material.VINE);
        DANGERS.add(Material.LADDER);
        DANGERS.add(Material.COBWEB);
        DANGERS.add(Material.AIR);
        DANGERS.add(Material.TRIPWIRE);
        DANGERS.add(Material.TRIPWIRE_HOOK);
        DANGERS.add(Material.SWEET_BERRY_BUSH);
        DANGERS.add(Material.MAGMA_BLOCK);
        DANGERS.add(Material.SEAGRASS);
        DANGERS.add(Material.TALL_SEAGRASS);

        EXCLUDES = new ArrayList<>();
        EXCLUDES.add(Material.AIR);
        EXCLUDES.add(Material.GRASS);
        EXCLUDES.add(Material.TALL_GRASS);
        EXCLUDES.add(Material.DANDELION);
        EXCLUDES.add(Material.POPPY);
        EXCLUDES.add(Material.BLUE_ORCHID);
        EXCLUDES.add(Material.ALLIUM);
        EXCLUDES.add(Material.AZURE_BLUET);
        EXCLUDES.add(Material.RED_TULIP);
        EXCLUDES.add(Material.WHITE_TULIP);
        EXCLUDES.add(Material.ORANGE_TULIP);
        EXCLUDES.add(Material.PINK_TULIP);
        EXCLUDES.add(Material.OXEYE_DAISY);
        EXCLUDES.add(Material.CORNFLOWER);
        EXCLUDES.add(Material.LILY_OF_THE_VALLEY);
        EXCLUDES.add(Material.SUNFLOWER);
        EXCLUDES.add(Material.LILAC);
        EXCLUDES.add(Material.ROSE_BUSH);
        EXCLUDES.add(Material.PEONY);
        EXCLUDES.add(Material.DEAD_BUSH);
        EXCLUDES.add(Material.FERN);
        EXCLUDES.add(Material.LARGE_FERN);
    }

    public static Location getRandomLocation(int bound) {
        int dbound = bound * 2;
        int x = CommonUtil.random.nextInt(dbound) - bound;
        int z = CommonUtil.random.nextInt(dbound) - bound;
        //todo: test this method w/o loading chunk
        return Bukkit.getWorlds().get(0).getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
    }

    public static Location getDistanceLocation(Location src, int dist) {
        double angle = Math.random() * Math.PI * 2;
        int x = src.getBlockX() + (int) (Math.cos(angle) * dist);
        int z = src.getBlockZ() + (int) (Math.sin(angle) * dist);
        //todo: test this method w/o loading chunk
        return Bukkit.getWorlds().get(0).getHighestBlockAt(x, z).getLocation().add(0.5, 1, 0.5);
    }

    public static boolean isSafeLocation(Location l) {
        l.getChunk().load();
        Block groundBlock = l.getBlock().getRelative(0, -1, 0);
        Material m = groundBlock.getType();
        if (groundBlock.isLiquid()) {
            LoggerUtil.debug(String.format(SAFELOC_FAIL, SAFELOC_FAIL_LIQUID, formatLocation(groundBlock.getLocation())));
            return false;
        }
        if (DANGERS.contains(m)) {
            LoggerUtil.debug(String.format(SAFELOC_FAIL, m.name(), formatLocation(groundBlock.getLocation())));
            return false;
        }
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++)
                for (int k = 1; k <= 2; k++)
                    if (!EXCLUDES.contains(groundBlock.getRelative(i, k, j).getType())) {
                        LoggerUtil.debug(String.format(SAFELOC_FAIL, SAFELOC_FAIL_OBSTRUCTION, formatLocation(groundBlock.getLocation())));
                        return false;
                    }
        return true;
    }

    public static double distance2d(Location l1, Location l2) {
        //return max distance if both worlds are specified and not equals
        if (l1.getWorld() != null && l2.getWorld() != null && l1.getWorld() != l2.getWorld())
            return DISTANCE_MAX;
        int x = l1.getBlockX() - l2.getBlockX();
        int z = l1.getBlockZ() - l2.getBlockZ();
        return Math.sqrt(x * x + z * z);
    }

    public static String formatLocation(Location l) {
        return l.getWorld().getName() + COMMA +
                l.getBlockX() + COMMA +
                l.getBlockY() + COMMA +
                l.getBlockZ();
    }

    public static Location safetizeLocation(Location l) {
        World w = l.getWorld();
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        //fill stone
        Filler f = new Filler().setWorld(w).setMaterial(Material.STONE)
                .setStartX(x - 2).setEndX(x + 2)
                .setStartY(y - 2).setEndY(y - 2)
                .setStartZ(z - 2).setEndZ(z + 2);
        f.fill();
        //fill ground log
        f.setMaterial(Material.OAK_LOG).setStartY(y - 1).setEndY(y - 1).fill();
        //clear spawn area
        f.setMaterial(Material.AIR).setStartY(y).setEndY(y + 2).fill();
        LoggerUtil.debug(String.format(SAFETIZE, formatLocation(l)));
        return l;
    }

    @SuppressWarnings("ConstantConditions")
    public static void invToChest(Inventory inv, Location l) {
        //todo test: possible NPE, but chesttracker created after game start
        ChestTracker ct = Engine.getInstance().getChestTracker();
        World w = l.getWorld();
        //prevent attempts to create chests outside the world
        if (l.getY() < 0 || l.getY() > w.getMaxHeight())
            return;

        Block b = w.getBlockAt(l);
        int slots = 0;
        b.setType(Material.CHEST);
        ct.detectBlock(b);
        Inventory chestInv = ((Chest) b.getState()).getBlockInventory();
        for (ItemStack is : inv.getContents()) {
            //suppressed warning here. ItemStack CAN be null!
            if (is != null) {
                //switch to next chest when filled
                if (slots == 27) {
                    b = w.getBlockAt(l.getBlockX(), l.getBlockY() + 1, l.getBlockZ());
                    b.setType(Material.CHEST);
                    chestInv = ((Chest) b.getState()).getBlockInventory();
                }
                //put item
                chestInv.addItem(is);
                slots++;
            }
        }
    }

    public static void generateBarrier(List<Block> blocks) {
        if (blocks.size() == 0)
            return;
        Block b0 = blocks.get(0);
        int minX = b0.getX();
        int maxX = minX;
        int minZ = b0.getZ();
        int maxZ = minZ;
        int maxY = b0.getY();
        if (blocks.size() > 1)
            for (Block b : blocks.subList(1, blocks.size())) {
                int bx = b.getX();
                int by = b.getY();
                int bz = b.getZ();
                if (bx < minX) minX = bx;
                else if (bx > maxX) maxX = bx;
                if (bz < minZ) minZ = bz;
                else if (bz > maxZ) maxZ = bz;
                if (by > maxY) maxY = by;
            }
        if (maxY > 248) {
            new Filler().setWorld(b0.getWorld()).setMaterial(Material.BARRIER)
                    .setStartX(minX-1).setEndX(maxX+1)
                    .setStartY(255).setEndY(255)
                    .setStartZ(minZ-1).setEndZ(maxZ+1)
                    .fill();
        }
    }

    private WorldUtil() {
    }
}