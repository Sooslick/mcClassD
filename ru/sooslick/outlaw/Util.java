import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;

public class Util {

    private static Random random = new Random();
    public static final List<Material> DANGERS;

    static {
        Material[] dgrs = {Material.FIRE, Material.TNT, Material.CACTUS, Material.VINE, Material.MAGMA, Material.LADDER, Material.TRAP_DOOR,
                Material.IRON_TRAPDOOR, Material.TRIPWIRE, Material.TRIPWIRE_HOOK, Material.GOLD_PLATE, Material.IRON_PLATE, Material.STONE_PLATE, Material.WOOD_PLATE,
                Material.SAND, Material.GRAVEL};
        //todo: add campfire, kystik, pistons,...
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
        for (int i=0; i<10; i++) {      //10 attempts to get safe loc
            Location l = getRandomLocation(bound);
            if (isSafeLocation(l))
                return l;
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
        Location l = src;               // init var with src value due to compile error
        for (int i=0; i<10; i++) {      //10 attempts to get safe loc
            l = getDistanceLocation(src, dist);
            if (isSafeLocation(l))
                return l;
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
