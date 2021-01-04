package ru.sooslick.outlaw;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import ru.sooslick.outlaw.roles.Outlaw;

import java.util.HashMap;

public class TrackedLocationCache {
    private final HashMap<World, Location> cache;
    private final int refreshPeriod;
    private final Outlaw outlaw;

    private long lastRefreshTime;

    public TrackedLocationCache(Outlaw outlaw) {
        cache = new HashMap<>();
        lastRefreshTime = 0;
        refreshPeriod = Math.min(Cfg.compassUpdatesPeriod, 20);  //20 seconds is too much for games with manual compass updates
        this.outlaw = outlaw;
    }

    public Location getTrackedLocation(World w) {
        //check is cache requires refreshing;
        long timer = Engine.getInstance().getGameTimer();
        if (timer - lastRefreshTime >= refreshPeriod) {
            cache.clear();
            lastRefreshTime = timer;
        }
        //get from cache if not null
        Location trackedLocation = cache.get(w);
        if (trackedLocation != null)
            return trackedLocation;
        //get actual tracked pos and put it to cache and create lodestone for nether
        trackedLocation = outlaw.getTrackedLocation(w);
        if (w.getEnvironment() == World.Environment.NETHER) {
            trackedLocation.setY(128);
            trackedLocation.getBlock().setType(Material.LODESTONE);
        }
        cache.put(w, trackedLocation);
        return trackedLocation;
    }
}
