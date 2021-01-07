package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.GameState;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.CommonUtil;
import ru.sooslick.outlaw.util.Filler;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.time.Duration;
import java.util.LinkedList;

public class Wall {
    private static final String DEBUG_LIMITER = "Wall limiter is %s. Expected volume: %s";
    private static final String DEBUG_RESET_SPOTS = "Reset and launched buildSpotTick";
    private static final String DEBUG_RESET_WALL = "Reset wall generator and launched buildWallTick";
    private static final String DEBUG_SPOTS_FINISHED = "buildSpots finished";
    private static final String DEBUG_SPOTS_QUEUED = "buildSpots queued, wallBuilt = ";
    private static final String DEBUG_WALL_FINISHED = "buildWall finished, spotsQueued = ";
    private static final String WARN_BUILD_LIMIT_TOO_SMALL = "blocksPerSecondLimit value too small, fixed it";

    private static int generatorTimerId;
    private static WallGameModeConfig wallCfg;
    private static LinkedList<Integer> spotPositions;
    private static int groundCurr;
    private static int airCurr;
    private static int undergroundCurr;
    private static int size;
    private static int halfSize;
    private static int spotSize;
    private static int side;                        //current side of square
    private static int currentBlock;                //current block of side
    private static int limiter;
    private static int startWallCoord;
    private static int endWallCoord;
    private static boolean wallBuilt;
    private static boolean spotsQueued;
    private static World w;

    private final static Runnable buildWallTick = () -> {
        int from = currentBlock;
        int to = currentBlock + limiter - 1;
        if (to > endWallCoord)
            to = endWallCoord;
        Filler f = getSideBasedFiller(side, from, to)
                .setStartY(0)
                .setEndY(255)
                .setMaterial(Material.BEDROCK);
        if (f.fill()) {
            currentBlock+= limiter;
            if (currentBlock >= endWallCoord) {
                currentBlock = -startWallCoord;
                side++;
                if (side > 3) {
                    Bukkit.getScheduler().cancelTask(generatorTimerId);
                    LoggerUtil.debug(DEBUG_WALL_FINISHED + spotsQueued);
                    if (Engine.getInstance().getGameState() == GameState.IDLE)
                        Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
                    wallBuilt = true;
                    if (spotsQueued) {
                        launchBuildSpots();
                    }
                }
            }
        }
    };

    private final static Runnable buildSpotTick = () -> {
        int center = spotPositions.getFirst();
        Filler f = getSideBasedFiller(side, center-spotSize, center+spotSize).setMaterial(Material.OBSIDIAN);
        if (groundCurr < wallCfg.groundSpotQty) {
            int h = getGroundLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            groundCurr++;
        } else if (airCurr < wallCfg.airSpotQty) {
            int h = getAirLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            airCurr++;
        } else if (undergroundCurr < wallCfg.undergroundSpotQty) {
            int h = getUndergroundLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            if (++undergroundCurr >= wallCfg.undergroundSpotQty) {
                groundCurr = 0;
                airCurr = 0;
                undergroundCurr = 0;
                if (++side > 3) {
                    Bukkit.getScheduler().cancelTask(generatorTimerId);
                    LoggerUtil.debug(DEBUG_SPOTS_FINISHED);
                }
            }
        }
        spotPositions.removeFirst();
    };

    //disable constructor for utility class
    private Wall() {}

    static String getWaitDuration() {
        long seconds = (size / limiter + 1) * 4;
        return CommonUtil.formatDuration(Duration.ofSeconds(seconds));
    }

    static void buildWall(WallGameModeConfig cfg) {
        //stop previous generator if it still working, clear
        Bukkit.getScheduler().cancelTask(generatorTimerId);
        wallCfg = cfg;
        wallBuilt = false;
        spotsQueued = false;
        size = wallCfg.playzoneSize;
        halfSize = size / 2;
        startWallCoord = halfSize + 1;
        endWallCoord = startWallCoord + wallCfg.wallThickness - 1;
        side = 0;
        currentBlock = -startWallCoord;         //from -start to +end
        limiter = Cfg.blocksPerSecondLimit / 256 / wallCfg.wallThickness;
        LoggerUtil.debug(String.format(DEBUG_LIMITER, limiter, limiter*256*wallCfg.wallThickness));
        if (limiter == 0) {
            limiter = 1;
            Cfg.blocksPerSecondLimit = wallCfg.wallThickness * 256;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL);
        }
        w = Bukkit.getWorlds().get(0);

        //launch
        generatorTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), buildWallTick, 1, 20);
        LoggerUtil.debug(DEBUG_RESET_WALL);
    }

    private static void launchBuildSpots() {
        spotSize = wallCfg.spotSize;
        side = 0;
        groundCurr = 0;
        undergroundCurr = 0;
        airCurr = 0;

        //pre-generate spots
        spotPositions = new LinkedList<>();
        int total = (wallCfg.airSpotQty + wallCfg.groundSpotQty + wallCfg.undergroundSpotQty) * 4;
        for (int i = 0; i < total; i++) {
            spotPositions.add(CommonUtil.random.nextInt(size) - halfSize);
        }

        //launch
        generatorTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), buildSpotTick, 1, 1);
        LoggerUtil.debug(DEBUG_RESET_SPOTS);
    }

    static void buildSpots() {
        LoggerUtil.debug(DEBUG_SPOTS_QUEUED + wallBuilt);
        if (wallBuilt) {
            launchBuildSpots();
        } else {
            spotsQueued = true;
        }
    }

    static void kill() {
        //stop previous generator if it still working, clear
        Bukkit.getScheduler().cancelTask(generatorTimerId);
    }

    private static Filler getSideBasedFiller(int side, int from, int to) {
        Filler f = new Filler().setWorld(w);
        switch (side) {
            case 0:         //+x
                f.setStartX(startWallCoord).setEndX(endWallCoord)
                        .setStartZ(from).setEndZ(to);
                break;
            case 1:         //+z
                f.setStartZ(startWallCoord).setEndZ(endWallCoord)
                        .setStartX(from).setEndX(to);
                break;
            case 2:         //-x
                f.setStartX(-endWallCoord).setEndX(-startWallCoord)
                        .setStartZ(from).setEndZ(to);
                break;
            case 3:         //-z
                f.setStartZ(-endWallCoord).setEndZ(-startWallCoord)
                        .setStartX(from).setEndX(to);
        }
        return f;
    }

    private static int getGroundLevel(int side, int center) {
        Block b;
        switch (side) {
            case 0:                 //+x
                b = w.getBlockAt(startWallCoord-1, 0, center);
                break;
            case 1:                 //+z
                b = w.getBlockAt(center, 0, startWallCoord-1);
                break;
            case 2:                 //-x
                b = w.getBlockAt(-startWallCoord+1, 0, center);
                break;
            case 3:                 //-z
                b = w.getBlockAt(center, 0, -startWallCoord+1);
                break;
            default: return 65;
        }
        w.loadChunk(b.getChunk());
        return w.getHighestBlockAt(b.getLocation()).getY() + 1;
    }

    private static int getAirLevel(int side, int center) {
        int groundLevel = getGroundLevel(side, center);
        return CommonUtil.random.nextInt(240 - groundLevel) + groundLevel + spotSize;
    }

    private static int getUndergroundLevel(int side, int center) {
        int groundLevel = getGroundLevel(side, center);
        return CommonUtil.random.nextInt(groundLevel - spotSize) + spotSize;
    }

    //todo: adequate task queue + rollback feature
}
