package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.CommonUtil;
import ru.sooslick.outlaw.util.Filler;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class Wall {
    // strings
    private static final String DEBUG_LIMITER = "Wall limiter is %s. Expected volume: %s";
    private static final String INFO_GENERATE_CHUNKS = "Started generating new chunks (first wall building or play area extension)";
    private static final String INFO_GENERATE_CHUNKS_END = "Wall chunks generated";
    private static final String INFO_GENERATE_SPOTS = "Started generating exit spots";
    private static final String INFO_GENERATE_SPOTS_END = "Exit spots generated";
    private static final String INFO_GENERATE_WALL = "Started generating wall";
    private static final String INFO_ROLLBACK_SPOTS = "Started removing exit spots";
    private static final String INFO_ROLLBACK_SPOTS_END = "All exit spots removed";
    private static final String INFO_ROLLBACK_WALL = "Started removing wall";
    private static final String INFO_ROLLBACK_WALL_END = "Wall removed";
    private static final String WARN_BUILD_LIMIT_TOO_SMALL = "blocksPerSecondLimit value too small, fixed it. New limit is ";

    private static final long NOTIFY_INTERVAL = 25565;

    // general
    private final WallGameModeConfig wallCfg;

    // world
    private final Set<Chunk> chunks = new HashSet<>();
    private List<Filler> wallParts = new LinkedList<>();
    private final List<Filler> spots = new LinkedList<>();
    private final List<Filler> spotBalconies = new LinkedList<>();
    private final World world = Bukkit.getWorlds().get(0);

    // limiters
    private static final int maxY = Bukkit.getWorlds().get(0).getMaxHeight() - 1;
    private int size = 0;
    private int thickness = 0;
    private int startWallCoord = 0;
    private int endWallCoord = 0;

    // spot limiters
    private int spotSize;
    private int groundSpots;
    private int airSpots;
    private int undergroundSpots;
    private int spotLimiter;

    // current state
    private int cProceeded = 0;
    private int proceeded = 0;
    private double cPercent = 0;
    private double percent = 0;
    private Iterator<Chunk> chunkIterator;
    private Iterator<Filler> wallIterator;
    private Iterator<Filler> spotIterator;
    private boolean wallGenerated = false;
    private boolean wallGenQueued = false;

    //performance
    private long lastNotifyTime;
    private long lastTickStartTime;
    private long cLastTickStartTime;
    private int cSkips = 0;
    private int skips = 0;

    public Wall(WallGameModeConfig cfg) {
        wallCfg = cfg;
    }

    public void prepareWall() {
        // sizes
        int oldSize = size;
        int oldThickness = thickness;
        size = wallCfg.playzoneSize;
        thickness = wallCfg.wallThickness;
        wallGenQueued = true;

        int halfSize = size / 2;
        startWallCoord = halfSize + 1;
        endWallCoord = startWallCoord + thickness - 1;

        boolean reqRebuild = wallGenerated && (size != oldSize || thickness != oldThickness);
        if (reqRebuild)
            rollbackWallInit();
        else if (wallGenerated)
            rollbackSpotsInit();
        else
            generateChunksInit();
    }

    public void prepareSpots() {
        spotSize = wallCfg.spotSize;
        groundSpots = wallCfg.groundSpotQty;
        airSpots = wallCfg.airSpotQty;
        undergroundSpots = wallCfg.undergroundSpotQty;
        generateSpotsInit();
    }

    public void rollback() {
        wallGenQueued = false;
        rollbackWallInit();
    }

    private void generateChunksInit() {
        LoggerUtil.info(INFO_GENERATE_CHUNKS);
        int cThickness = calcChunk(endWallCoord) - calcChunk(startWallCoord);

        // fill gen queue
        // +x
        for (int cz = calcChunk(-startWallCoord); cz <= calcChunk(endWallCoord); cz++)
            for (int cx = calcChunk(startWallCoord); cx <= calcChunk(startWallCoord) + cThickness; cx++)
                chunks.add(world.getChunkAt(cx, cz));
        // +z
        for (int cx = calcChunk(startWallCoord); cx >= calcChunk(-endWallCoord); cx--)
            for (int cz = calcChunk(startWallCoord); cz <= calcChunk(startWallCoord) + cThickness; cz++)
                chunks.add(world.getChunkAt(cx, cz));
        // -x
        for (int cz = calcChunk(startWallCoord); cz >= calcChunk(-endWallCoord); cz--)
            for (int cx = calcChunk(-startWallCoord); cx >= calcChunk(-startWallCoord) - cThickness; cx--)
                chunks.add(world.getChunkAt(cx, cz));
        // -z
        for (int cx = calcChunk(-startWallCoord); cx <= calcChunk(endWallCoord); cx++)
            for (int cz = calcChunk(-startWallCoord); cz >= calcChunk(-startWallCoord) - cThickness; cz--)
                chunks.add(world.getChunkAt(cx, cz));

        //launch job
        cPercent = 0;
        cProceeded = 0;
        cLastTickStartTime = System.currentTimeMillis();
        cSkips = 0;
        chunkIterator = chunks.iterator();
        scheduleTick(this::generateChunksTick, 1);
    }

    private void generateWallInit() {
        LoggerUtil.info(INFO_GENERATE_WALL);
        int limiter = Cfg.blocksPerSecondLimit / 256 / thickness;
        if (limiter < 2) {
            limiter = 2;
            Cfg.blocksPerSecondLimit = thickness * 512;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        }
        LoggerUtil.debug(String.format(DEBUG_LIMITER, limiter, limiter * 256 * thickness));

        // fill wall queue
        // +x
        for (int z = -startWallCoord; z <= endWallCoord; z += limiter) {
            int endZ = Math.min(z + limiter - 1, endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startWallCoord).setEndX(endWallCoord)
                    .setStartZ(z).setEndZ(endZ));
        }
        // +z
        for (int x = startWallCoord; x >= -endWallCoord; x -= limiter) {
            int startX = Math.max(x - limiter + 1, -endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startX).setEndX(x)
                    .setStartZ(startWallCoord).setEndZ(endWallCoord));
        }
        // -x
        for (int z = startWallCoord; z >= -endWallCoord; z -= limiter) {
            int startZ = Math.max(z - limiter + 1, -endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(-endWallCoord).setEndX(-startWallCoord)
                    .setStartZ(startZ).setEndZ(z));
        }
        // -z
        for (int x = -startWallCoord; x <= endWallCoord; x += limiter) {
            int endX = Math.min(x + limiter - 1, endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(x).setEndX(endX)
                    .setStartZ(-endWallCoord).setEndZ(-startWallCoord));
        }

        //launch job
        skips = 0;
        proceeded = 0;
        lastTickStartTime = System.currentTimeMillis();
        wallIterator = wallParts.listIterator();
        scheduleTick(this::generateWallTick, 1);
    }

    private void rollbackWallInit() {
        LoggerUtil.info(INFO_ROLLBACK_WALL);
        // split fillers to fit new limit
        int maxVolume = calcMaxFillerVolume(wallParts);
        int cfgVolume = Cfg.blocksPerSecondLimit;
        if (cfgVolume < maxVolume) {
            // pre-calc dividers
            double divider = (double) maxVolume / cfgVolume;
            int limiter = Math.max((int) Math.ceil((maxY + 1) / divider), 1);
            // map original list
            List<Filler> dividedParts = new LinkedList<>();
            for (Filler original : wallParts)
                for (int endY = maxY; endY >= 0; endY -= limiter)
                    dividedParts.add(original.copy().setEndY(endY).setStartY(Math.max(endY - limiter + 1, 0)));
            wallParts = dividedParts;
            // re-calc bps limit
            maxVolume = calcMaxFillerVolume(wallParts);
            Cfg.blocksPerSecondLimit = maxVolume + 1;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        }

        //launch job
        skips = 0;
        proceeded = 0;
        percent = 0;
        lastTickStartTime = System.currentTimeMillis();
        wallIterator = wallParts.listIterator();
        scheduleTick(this::rollbackWallTick, 1);
    }

    private void generateSpotsInit() {
        LoggerUtil.info(INFO_GENERATE_SPOTS);
        // calc spot volume and limiters
        int spotVolume = thickness * spotSize * spotSize;
        spotLimiter = (int) Math.floor((double) Cfg.blocksPerSecondLimit / spotVolume);
        if (spotLimiter < 1) {
            spotLimiter = 1;
            Cfg.blocksPerSecondLimit = spotVolume + 1;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        }

        // pre calc spot coordinates
        int spotsPerSide = undergroundSpots + groundSpots + airSpots;
        LinkedList<Integer> positions = new LinkedList<>();
        for (int i = 0; i < spotsPerSide * 4; i++)
            positions.add(CommonUtil.random.nextInt(size - spotSize) - size / 2);
        LinkedList<Integer> heights = new LinkedList<>();
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < undergroundSpots; i++) heights.add(-1);
            for (int i = 0; i < groundSpots; i++) heights.add(0);
            for (int i = 0; i < airSpots; i++) heights.add(1);
        }

        // generate fillers
        // +x
        for (int i = 0; i < spotsPerSide; i++) {
            int startZ = positions.removeFirst();
            int testX = startWallCoord - 1;
            world.getBlockAt(testX, 0, startZ).getChunk().load();
            int groundY = getRandomHeigth(world.getHighestBlockYAt(testX, startZ), spotSize, heights.removeFirst());
            Filler spotFiller = getTemplateFiller(world, Material.OBSIDIAN)
                    .setStartX(startWallCoord).setEndX(endWallCoord)
                    .setStartY(groundY).setEndY(groundY + spotSize - 1)
                    .setStartZ(startZ).setEndZ(startZ + spotSize - 1);
            spots.add(spotFiller);
            if (groundY > 64)
                spotBalconies.add(spotFiller.copy().setMaterial(Material.GLASS)
                        .setStartX(endWallCoord + 1).setEndX(endWallCoord + 1)
                        .setStartY(groundY - 1).setEndY(groundY - 1));
        }
        // +z
        for (int i = 0; i < spotsPerSide; i++) {
            int startX = positions.removeFirst();
            int testZ = startWallCoord - 1;
            world.getBlockAt(startX, 0, testZ).getChunk().load();
            int groundY = getRandomHeigth(world.getHighestBlockYAt(startX, testZ), spotSize, heights.removeFirst());
            Filler spotFiller = getTemplateFiller(world, Material.OBSIDIAN)
                    .setStartX(startX).setEndX(startX + spotSize - 1)
                    .setStartY(groundY).setEndY(groundY + spotSize - 1)
                    .setStartZ(startWallCoord).setEndZ(endWallCoord);
            spots.add(spotFiller);
            if (groundY > 64)
                spotBalconies.add(spotFiller.copy().setMaterial(Material.GLASS)
                        .setStartZ(endWallCoord + 1).setEndZ(endWallCoord + 1)
                        .setStartY(groundY - 1).setEndY(groundY - 1));
        }
        // -x
        for (int i = 0; i < spotsPerSide; i++) {
            int startZ = positions.removeFirst();
            int testX = startWallCoord - 1;
            world.getBlockAt(testX, 0, startZ).getChunk().load();
            int groundY = getRandomHeigth(world.getHighestBlockYAt(testX, startZ), spotSize, heights.removeFirst());
            Filler spotFiller = getTemplateFiller(world, Material.OBSIDIAN)
                    .setStartX(-endWallCoord).setEndX(-startWallCoord)
                    .setStartY(groundY).setEndY(groundY + spotSize - 1)
                    .setStartZ(startZ).setEndZ(startZ + spotSize - 1);
            spots.add(spotFiller);
            if (groundY > 64)
                spotBalconies.add(spotFiller.copy().setMaterial(Material.GLASS)
                        .setStartX(-endWallCoord - 1).setEndX(-endWallCoord - 1)
                        .setStartY(groundY - 1).setEndY(groundY - 1));
        }
        // -z
        for (int i = 0; i < spotsPerSide; i++) {
            int startX = positions.removeFirst();
            int testZ = startWallCoord - 1;
            world.getBlockAt(startX, 0, testZ).getChunk().load();
            int groundY = getRandomHeigth(world.getHighestBlockYAt(startX, testZ), spotSize, heights.removeFirst());
            Filler spotFiller = getTemplateFiller(world, Material.OBSIDIAN)
                    .setStartX(startX).setEndX(startX + spotSize - 1)
                    .setStartY(groundY).setEndY(groundY + spotSize - 1)
                    .setStartZ(-endWallCoord).setEndZ(-startWallCoord);
            spots.add(spotFiller);
            if (groundY > 64)
                spotBalconies.add(spotFiller.copy().setMaterial(Material.GLASS)
                        .setStartZ(-endWallCoord - 1).setEndZ(-endWallCoord - 1)
                        .setStartY(groundY - 1).setEndY(groundY - 1));
        }

        //run job
        spotIterator = spots.listIterator();
        scheduleTick(this::generateSpotsTick, 1);
    }

    private void rollbackSpotsInit() {
        LoggerUtil.info(INFO_ROLLBACK_SPOTS);
        // calc max volume
        int maxVolume = calcMaxFillerVolume(spots);
        if (Cfg.blocksPerSecondLimit < maxVolume) {
            Cfg.blocksPerSecondLimit = maxVolume + 1;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        }

        // run job
        spotLimiter = (int) Math.floor((double) Cfg.blocksPerSecondLimit / maxVolume);
        spotIterator = spots.listIterator();
        scheduleTick(this::rollbackSpotsTick, 1);
    }

    private void generateChunksTick() {
        long time = System.currentTimeMillis();
        // progress notification
        if (time - lastNotifyTime > NOTIFY_INTERVAL) {
            Bukkit.broadcastMessage(String.format(Messages.WALL_CHUNK_GEN_PROGRESS, (int) (cPercent * 100)));
            lastNotifyTime = time;
        }
        // check skip
        long duration = time - cLastTickStartTime;
        cLastTickStartTime = time;
        if (duration > 100) {   // skip if current tps degraded to 10 and lower
            cSkips++;
            // force generate chunk if skipped too much ticks
            if (cSkips < 10) {
                scheduleTick(this::generateChunksTick, 1);
                return;
            }
        }
        // proceed
        cSkips = 0;
        if (chunkIterator.hasNext()) {
            // generate chunk
            chunkIterator.next().load(true);
            scheduleTick(this::generateChunksTick, 1);
            // update info and start gen wall
            double oldPercent = cPercent;
            cPercent = (double) ++cProceeded / chunks.size();
            if (oldPercent < 0.25d && cPercent >= 0.25d)
                generateWallInit();
        } else {
            // prune queue and finish task
            cPercent = 1;
            chunks.clear();
            LoggerUtil.info(INFO_GENERATE_CHUNKS_END);
        }
    }

    private void generateWallTick() {
        long time = System.currentTimeMillis();
        // progress notification
        if (time - lastNotifyTime > NOTIFY_INTERVAL) {
            Bukkit.broadcastMessage(String.format(Messages.WALL_GEN_PROGRESS, (int) (percent * 100)));
            lastNotifyTime = time;
        }
        // check skip
        long duration = time - lastTickStartTime;
        lastTickStartTime = time;
        if (duration > 2000) {   // skip if current tps degraded to 10 and lower
            skips++;
            // force generate wall piece if skipped too much ticks
            if (skips < 10) {
                scheduleTick(this::generateWallTick, 20);
                return;
            }
        }
        // assert that enough chunks checked
        if (cPercent < 1) {
            if (cPercent - percent < 0.25) {
                scheduleTick(this::generateWallTick, 20);
                return;
            }
        }
        //proceed
        skips = 0;
        if (wallIterator.hasNext()) {
            wallIterator.next().fill();
            scheduleTick(this::generateWallTick, 20);
            percent = (double) ++proceeded / wallParts.size();
        } else {
            wallGenerated = true;
            Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
        }
    }

    private void rollbackWallTick() {
        long time = System.currentTimeMillis();
        // progress notification
        if (time - lastNotifyTime > NOTIFY_INTERVAL) {
            Bukkit.broadcastMessage(String.format(Messages.WALL_GEN_ROLLBACK_PROGRESS, (int) (percent * 100)));
            lastNotifyTime = time;
        }
        // check skip
        long duration = time - lastTickStartTime;
        lastTickStartTime = time;
        if (duration > 2000) {   // skip if current tps degraded to 10 and lower
            skips++;
            // force generate wall piece if skipped too much ticks
            if (skips < 10) {
                scheduleTick(this::generateWallTick, 20);
                return;
            }
        }
        //proceed
        skips = 0;
        if (wallIterator.hasNext()) {
            Filler current = wallIterator.next();
            int startY = current.getStartY();
            int endY = current.getEndY();
            if (startY > 63)
                current.setMaterial(Material.AIR).fill();
            else if (endY <= 63)
                current.setMaterial(Material.STONE).fill();
            else {
                current.setEndY(63).setMaterial(Material.STONE).fill();
                current.setStartY(64).setEndY(endY).setMaterial(Material.AIR).fill();
            }
            scheduleTick(this::rollbackWallTick, 20);
            percent = (double) ++proceeded / wallParts.size();
        } else {
            wallGenerated = false;
            wallParts.clear();
            if (wallGenQueued)
                generateWallInit();
            LoggerUtil.info(INFO_ROLLBACK_WALL_END);
        }
    }

    private void generateSpotsTick() {
        if (!wallGenerated) {
            scheduleTick(this::generateSpotsTick, 20);
            return;
        }
        int currentSpot = 0;
        while (currentSpot < spotLimiter && spotIterator.hasNext()) {
            currentSpot++;
            spotIterator.next().fill();
        }
        if (spotIterator.hasNext())
            scheduleTick(this::generateSpotsTick, 20);
        else {
            spotBalconies.forEach(Filler::fill);
            LoggerUtil.info(INFO_GENERATE_SPOTS_END);
        }
    }

    private void rollbackSpotsTick() {
        int currentSpot = 0;
        while (currentSpot < spotLimiter && spotIterator.hasNext()) {
            currentSpot++;
            spotIterator.next().setMaterial(Material.BEDROCK).fill();
        }
        if (spotIterator.hasNext())
            scheduleTick(this::generateSpotsTick, 20);
        else {
            spotBalconies.forEach(filler -> filler.setMaterial(Material.AIR).fill());
            LoggerUtil.info(INFO_ROLLBACK_SPOTS_END);
            spots.clear();
            spotBalconies.clear();
        }
    }

    // ======================================== //

    private static int calcChunk(int coord) {
        return (int) Math.floor((double) coord / 16d);
    }

    private static Filler getTemplateFiller(World world, Material mat) {
        return new Filler(world, mat)
                .setStartY(0)
                .setEndY(maxY);
    }

    private static int calcMaxFillerVolume(List<Filler> fillers) {
        return fillers.stream()
                .mapToInt(Filler::size)
                .max()
                .orElse(0);
    }

    private static void scheduleTick(Runnable task, int delay) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(Engine.getInstance(), task, delay);
    }

    private static int getRandomHeigth(int groundLevel, int deadzone, int level) {
        switch (level) {
            case -1:
                return CommonUtil.random.nextInt(groundLevel - deadzone - 2) + 1;
            case 1:
                return CommonUtil.random.nextInt(maxY - groundLevel - deadzone - 2) + groundLevel;
            default:
                return groundLevel;
        }
    }
}
