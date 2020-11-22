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
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

public class Util {

    public static Random random = new Random();
    public static final List<Material> DANGERS;
    private static final Logger LOGGER;

    static {
        LOGGER = Bukkit.getLogger();
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
    }

    public static <E> E getRandomOf(Collection<E> set) {
        return (E) set.toArray()[random.nextInt(set.size())];
    }

    public static Location getRandomLocation(int bound) {
        int dbound = bound * 2;
        int x = random.nextInt(dbound) - bound;
        if (x >= Cfg.playzoneSize)
            x = Cfg.playzoneSize - 1;
        else if (x <= -Cfg.playzoneSize)
            x = -Cfg.playzoneSize + 1;
        int z = random.nextInt(dbound) - bound;
        if (z >= Cfg.playzoneSize)
            z = Cfg.playzoneSize - 1;
        else if (z <= -Cfg.playzoneSize)
            z = -Cfg.playzoneSize + 1;
        return new Location(Bukkit.getWorlds().get(0), x, 64, z);
    }

    public static Location getSafeRandomLocation(int bound) {
        World w = Bukkit.getWorlds().get(0);
        Location l = w.getSpawnLocation();  //spawn location will have never used. Init variable due to compile error
        for (int i = 0; i < 10; i++) {      //10 attempts to get safe loc
            l = getRandomLocation(bound);
            l.getChunk().load();
            if (isSafeLocation(l)) {
                LOGGER.info("getSafeRandomLocation - success");
                return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
            }
        }
        LOGGER.info("getSafeRandomLocation - default");
        l = w.getHighestBlockAt(l).getLocation();
        safetizeLocation(l);
        return l.add(0.5, 1, 0.5);
    }

    public static Location getDistanceLocation(Location src, int dist) {
        double angle = Math.random() * Math.PI * 2;
        double x = src.getBlockX() + Math.round(Math.cos(angle) * dist);
        if (x >= Cfg.playzoneSize)
            x = Cfg.playzoneSize - 1;
        else if (x <= -Cfg.playzoneSize)
            x = -Cfg.playzoneSize + 1;
        double z = src.getBlockZ() + Math.round(Math.sin(angle) * dist);
        if (z >= Cfg.playzoneSize)
            z = Cfg.playzoneSize - 1;
        else if (z <= -Cfg.playzoneSize)
            z = -Cfg.playzoneSize + 1;
        return new Location(src.getWorld(), x, src.getBlockY(), z);
    }

    public static Location getSafeDistanceLocation(Location src, int dist) {
        World w = Bukkit.getWorlds().get(0);
        Location l = src;               // init var with src value due to compile error
        for (int i = 0; i < 10; i++) {      //10 attempts to get safe loc
            l = getDistanceLocation(src, dist);
            l.getChunk().load();
            if (isSafeLocation(l)) {
                LOGGER.info("getSafeDistanceLocation - success");
                return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
            }
        }
        LOGGER.info("getSafeDistanceLocation - default");
        l = w.getHighestBlockAt(l).getLocation();
        safetizeLocation(l);
        return l.add(0.5, 1, 0.5);
    }

    public static boolean isSafeLocation(Location l) {
        l.getChunk().load();                                //todo is necessary to load chunk? Already loaded in getSafeLocation
        Block b = l.getWorld().getHighestBlockAt(l);
        Material m = b.getType();
        if (b.isLiquid()) {
            LOGGER.info("isSafeLocation - fail, liquid // " + b.getLocation().toString());
            return false;
        }
        if (DANGERS.contains(m)) {
            LOGGER.info("isSafeLocation - fail, " + m.name() + " // " + b.getLocation().toString());
            return false;
        }
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++)
                for (int k = 1; k <= 2; k++)
                    if (!b.getRelative(i, k, j).getType().equals(Material.AIR)) {
                        LOGGER.info("isSafeLocation - fail, obstruction // " + b.getLocation().toString());
                        return false;
                    }
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
        l.getChunk().load();        //todo chunk already loaded in getSafeLocation...
        int x = l.getBlockX();
        int y = l.getBlockY();
        int z = l.getBlockZ();
        for (int i = x - 2; i <= x + 2; i++)
            for (int j = z - 2; j <= z + 2; j++) {
                w.getBlockAt(i, y - 1, j).setType(Material.STONE);
                w.getBlockAt(i, y, j).setType(Material.OAK_LOG);
                w.getBlockAt(i, y + 1, j).setType(Material.AIR);
                w.getBlockAt(i, y + 2, j).setType(Material.AIR);
                w.getBlockAt(i, y + 3, j).setType(Material.AIR);
            }
    }

    public static void invToChest(Inventory inv, Location l) {
        World w = l.getWorld();
        //prevent attempts to create chests outside the world
        if (l.getY() < 0 || l.getY() > w.getMaxHeight())
            return;

        Block b = w.getBlockAt(l);
        int slots = 0;
        b.setType(Material.CHEST);
        Inventory chestInv = ((Chest) b.getState()).getBlockInventory();
        for (ItemStack is : inv.getContents()) {
            if (is != null) {
                //switch to next chest when filled
                if (slots == 27) {
                    b = w.getBlockAt(l.getBlockX(), l.getBlockY()+1, l.getBlockZ());
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
