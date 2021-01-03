package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.HashMap;
import java.util.Map;

public class SafeLocationsHolder {
    private final static String DEBUG_CURRENT = "SafeLocations job - save current spawn locations";
    private final static String DEBUG_PENDING = "SafeLocations job - save pending location";
    private final static String DEBUG_RESERVATION = "SafeLocations job - save to reservation // size = ";
    private final static String JOB_LAUNCH = "SafeLocationsHolder - search job launched.";
    private final static String JOB_STOP = "SafeLocationsHolder - search job stopped. Reservation size: ";
    private final static String SELECT_CURRENT = "selectSafeLocations - selected by job, do nothing";
    private final static String SELECT_RANDOM = "selectSafeLocations - not selected, no reservations";
    private final static String SELECT_RESERVATION = "selectSafeLocations - not selected, get from reservations. Stored locations left: ";
    private final static int MAX_RESERVATION = 3;

    private Location pendingLocation;
    private Location safeLocationVictim;
    private Location safeLocationHunter;
    private Map<Location, Location> reservation;
    private int jobTaskId;

    public SafeLocationsHolder() {
        pendingLocation = null;
        safeLocationVictim = null;
        safeLocationHunter = null;
        reservation = new HashMap<>();
        jobTaskId = 0;
    }

    private final Runnable job = () -> {
        if (pendingLocation == null) {
            Location l = WorldUtil.getRandomLocation(Cfg.spawnRadius);
            if (WorldUtil.isSafeLocation(l)) {
                pendingLocation = l;
                LoggerUtil.debug(DEBUG_PENDING);
            }
        } else {
            Location l = WorldUtil.getDistanceLocation(pendingLocation, Cfg.spawnDistance);
            if (WorldUtil.isSafeLocation(l)) {
                if (safeLocationVictim == null || safeLocationHunter == null) {
                    safeLocationVictim = pendingLocation;
                    safeLocationHunter = l;
                    LoggerUtil.debug(DEBUG_CURRENT);
                } else {
                    reservation.put(pendingLocation, l);
                    LoggerUtil.debug(DEBUG_RESERVATION + reservation.size());
                }
                pendingLocation = null;
                if (reservation.size() >= MAX_RESERVATION)
                    stopJob();
            }
        }
    };

    public void launchJob() {
        pendingLocation = null;
        safeLocationVictim = null;
        safeLocationHunter = null;
        jobTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), job, 1, 1);
        LoggerUtil.debug(JOB_LAUNCH);
    }

    public void stopJob() {
        Bukkit.getScheduler().cancelTask(jobTaskId);
        LoggerUtil.debug(JOB_STOP + reservation.size());
    }

    public void selectSafeLocations() {
        stopJob();
        // if spawns not selected in lobby state
        if (safeLocationVictim == null || safeLocationHunter == null) {
            Map.Entry<Location, Location> resLoc = reservation.entrySet().stream().findFirst().orElse(null);
            if (resLoc == null) {
                //get ANY locations by default
                safeLocationVictim = WorldUtil.getRandomLocation(Cfg.spawnRadius);
                safeLocationHunter = WorldUtil.getDistanceLocation(safeLocationVictim, Cfg.spawnDistance);
                LoggerUtil.debug(SELECT_RANDOM);
            } else {
                //get ANY location from reservation and remove from hash
                safeLocationVictim = resLoc.getKey();
                safeLocationHunter = resLoc.getValue();
                reservation.remove(safeLocationVictim);
                LoggerUtil.debug(SELECT_RESERVATION + reservation.size());
            }
            return;
        }
        LoggerUtil.debug(SELECT_CURRENT);
    }

    public Location getVictimLocation() {
        return getSafeLocation(safeLocationVictim);
    }

    public Location getHunterLocation() {
        return getSafeLocation(safeLocationHunter);
    }

    private Location getSafeLocation(Location l) {
        //load chunk and check if loc still safe
        if (WorldUtil.isSafeLocation(l))
            return l;
        else
            return WorldUtil.safetizeLocation(l);
    }
}
