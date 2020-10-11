package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class Util {

    public static Random random = new Random();
    public static final List<Material> DANGERS;
    private static Logger logger;

    static {
        logger = Bukkit.getLogger();
        Material[] dgrs = {Material.FIRE, Material.CACTUS, Material.VINE, Material.LADDER, Material.COBWEB,
                Material.TRIPWIRE, Material.TRIPWIRE_HOOK, Material.SWEET_BERRY_BUSH, Material.MAGMA_BLOCK};
        DANGERS = new ArrayList<>(Arrays.asList(dgrs));
    }

    public static <E> E getRandomOf(Collection<E> set) {
        return (E) set.toArray()[random.nextInt(set.size())];
    }

    public static Location getRandomLocation(int bound) {
        //todo: world?
        int dbound = bound * 2;
        return new Location(Bukkit.getWorlds().get(0), random.nextInt(dbound) - bound, 64, random.nextInt(dbound) - bound);
    }

    public static Location getSafeRandomLocation(int bound) {
        World w = Bukkit.getWorlds().get(0);
        Location l = w.getSpawnLocation();  //spawn location will have never used. Init variable due to compile error
        for (int i = 0; i < 10; i++) {      //10 attempts to get safe loc
            l = getRandomLocation(bound);
            if (isSafeLocation(l)) {
                logger.info("getSafeRandomLocation - success");
                return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
            } else
                logger.info("getSafeRandomLocation - fail");
        }
        logger.info("getSafeRandomLocation - default");
        safetizeLocation(l);
        return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }

    public static Location getDistanceLocation(Location src, int dist) {
        double angle = Math.random() * Math.PI * 2;
        double x = Math.round(Math.cos(angle) * dist);
        double z = Math.round(Math.sin(angle) * dist);
        return new Location(src.getWorld(), src.getBlockX() + x, src.getBlockY(), src.getBlockZ() + z);
    }

    public static Location getSafeDistanceLocation(Location src, int dist) {
        World w = Bukkit.getWorlds().get(0);
        Location l = src;               // init var with src value due to compile error
        for (int i = 0; i < 10; i++) {      //10 attempts to get safe loc
            l = getDistanceLocation(src, dist);
            if (isSafeLocation(l)) {
                logger.info("getSafeDistanceLocation - success");
                return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
            } else
                logger.info("getSafeDistanceLocation - fail");
        }
        logger.info("getSafeDistanceLocation - default");
        safetizeLocation(l);
        return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }

    public static boolean isSafeLocation(Location l) {
        l.getWorld().loadChunk(l.getChunk());
        Block b = l.getWorld().getHighestBlockAt(l);
        if (b.getType().equals(Material.AIR)) return false;
        if (!b.getRelative(0, +1, 0).getType().equals(Material.AIR)) return false;
        if (!b.getRelative(0, +2, 0).getType().equals(Material.AIR)) return false;
        if (b.isLiquid()) return false;
        if (DANGERS.contains(b.getType())) return false;
        //todo check nearby blocks
        return true;
    }

    public static double distance(Location l1, Location l2) {
        int x = l1.getBlockX() - l2.getBlockX();
        int z = l1.getBlockZ() - l2.getBlockZ();
        return Math.sqrt(x * x + z * z);
    }

    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long h = seconds / 3600;
        if (h > 0) {
            return String.format(
                    "%d:%02d:%02d",
                    seconds / 3600,
                    (seconds % 3600) / 60,
                    seconds % 60);
        } else {
            return String.format(
                    "%d:%02d",
                    (seconds % 3600) / 60,
                    seconds % 60);
        }
    }

    public static void safetizeLocation(Location l) {
        World w = l.getWorld();
        w.loadChunk(l.getChunk());
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        for (int i = x - 2; i <= x + 2; i++)
            for (int j = z - 2; j <= z + 2; j++) {
                w.getBlockAt(i, y, j).setType(Material.OAK_LOG);
                w.getBlockAt(i, y+1, j).setType(Material.AIR);
                w.getBlockAt(i, y+2, j).setType(Material.AIR);
                w.getBlockAt(i, y+3, j).setType(Material.AIR);
            }
    }

    public static void invToChest(Inventory inv, Location l) {
        World w = l.getWorld();
        Block b = w.getBlockAt(l);
        int slots = 0;
        b.setType(Material.CHEST);
        Inventory chestInv = ((Chest) b.getState()).getBlockInventory();
        for (ItemStack is : inv.getContents()) {
            if (is != null) {
                //switch to next chest when filled
                if (slots == 27) {
                    b = w.getBlockAt(l.add(0, 1, 0));
                    b.setType(Material.CHEST);
                    chestInv = ((Chest) b.getState()).getBlockInventory();
                }
                //put item
                chestInv.addItem(is);
                slots++;
            }
        }
    }
}
