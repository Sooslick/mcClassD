package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.HashMap;
import java.util.Map;

class SafeLocationsHolder {
    private final static String DEBUG_CURRENT = "SafeLocations job - save current spawn locations";
    private final static String DEBUG_LIMIT = "SafeLocations job - too long search, stopping";
    private final static String DEBUG_LIMIT_PAIR = "SafeLocations job - cannot bind hunter spawn to victim spawn, let's try another place";
    private final static String DEBUG_PENDING = "SafeLocations job - save pending location";
    private final static String DEBUG_RESERVATION = "SafeLocations job - save to reservation // size = ";
    private final static String JOB_LAUNCH = "SafeLocationsHolder - search job launched.";
    private final static String JOB_STOP = "SafeLocationsHolder - search job stopped. Reservation size: ";
    private final static String SELECT_CURRENT = "selectSafeLocations - selected by job, do nothing";
    private final static String SELECT_RANDOM = "selectSafeLocations - not selected, no reservations";
    private final static String SELECT_RESERVATION = "selectSafeLocations - not selected, get from reservations. Stored locations left: ";
    private final static int MAX_RESERVATION = 3;
    private final static int MAX_TOTAL_ATTEMPTS = 200;
    private final static int MAX_PAIR_ATTEMPTS = 20;

    private Location pendingLocation;
    private Location safeLocationVictim;
    private Location safeLocationHunter;
    private Map<Location, Location> reservation;
    private int jobTaskId;
    private int pairAttempts;
    private int totalAttempts;

    SafeLocationsHolder() {
        pendingLocation = null;
        safeLocationVictim = null;
        safeLocationHunter = null;
        reservation = new HashMap<>();
        jobTaskId = 0;
    }

    private final Runnable job = () -> {
        if (pendingLocation == null) {
            totalAttempts++;
            pairAttempts = 0;
            Location l = WorldUtil.getRandomLocation(Cfg.spawnRadius);
            if (WorldUtil.isSafeLocation(l)) {
                pendingLocation = l;
                LoggerUtil.debug(DEBUG_PENDING);
            } else if (totalAttempts >= MAX_TOTAL_ATTEMPTS) {
                LoggerUtil.debug(DEBUG_LIMIT);
                stopJob();
            }
        } else {
            pairAttempts++;
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
            } else if (pairAttempts >= MAX_PAIR_ATTEMPTS) {
                LoggerUtil.debug(DEBUG_LIMIT_PAIR);
                pendingLocation = null;
            }
        }
    };

    void launchJob() {
        pendingLocation = null;
        safeLocationVictim = null;
        safeLocationHunter = null;
        totalAttempts = 0;
        pairAttempts = 0;
        // performance fix: added 1 second delay before launch and increased interval between repeats
        jobTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), job, 20, 4);
        LoggerUtil.debug(JOB_LAUNCH);
    }

    void stopJob() {
        Bukkit.getScheduler().cancelTask(jobTaskId);
        LoggerUtil.debug(JOB_STOP + reservation.size());
    }

    void selectSafeLocations() {
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

    Location getVictimLocation() {
        return getSafeLocation(safeLocationVictim);
    }

    Location getHunterLocation() {
        return getSafeLocation(safeLocationHunter);
    }

    Location getSafeLocation(Location l) {
        //load chunk and check if loc still safe
        if (WorldUtil.isSafeLocation(l))
            return l;
        else
            return WorldUtil.safetizeLocation(l);
    }
}
