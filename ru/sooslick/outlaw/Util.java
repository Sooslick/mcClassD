package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

public class Util {

    private static Random random = new Random();
    public static final List<Material> DANGERS;

    static {
        Material[] dgrs = {Material.FIRE, Material.TNT, Material.CACTUS, Material.VINE, Material.LADDER,
                Material.IRON_TRAPDOOR, Material.TRIPWIRE, Material.TRIPWIRE_HOOK,
                Material.SAND, Material.GRAVEL};
        //todo: add campfire, kystik, pistons, trapdoors, pressure plates, magmablock, ...
        DANGERS = new ArrayList<>(Arrays.asList(dgrs));
    }

    public static <E> E getRandomOf(Collection<E> set) {
        return (E) set.toArray()[random.nextInt(set.size())];
    }

    public static Location getRandomLocation(int bound) {
        //todo: world?
        return new Location(Bukkit.getWorlds().get(0), random.nextInt(bound), 64, random.nextInt(bound));
    }

    public static Location getSafeRandomLocation(int bound) {
        World w = Bukkit.getWorlds().get(0);
        for (int i=0; i<10; i++) {      //10 attempts to get safe loc
            Location l = getRandomLocation(bound);
            if (isSafeLocation(l))
                return w.getHighestBlockAt(l).getLocation().add(0,1,0);
        }
        //todo: adequate safe loc?
        return Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    public static Location getDistanceLocation(Location src, int dist) {
        double angle = Math.random() * Math.PI * 2;
        double x = Math.cos(angle) * dist;
        double z = Math.sin(angle) * dist;
        return src.add(x, 0, z);
    }

    public static Location getSafeDistanceLocation(Location src, int dist) {
        World w = Bukkit.getWorlds().get(0);
        Location l = src;               // init var with src value due to compile error
        for (int i=0; i<10; i++) {      //10 attempts to get safe loc
            l = getDistanceLocation(src, dist);
            if (isSafeLocation(l))
                return w.getHighestBlockAt(l).getLocation().add(0,1,0);
        }
        //todo: adequate safe loc? Returns NOT SAFE location!
        return l;
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
}
