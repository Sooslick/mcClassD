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
import ru.sooslick.outlaw.util.WorldUtil;

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
    private final Set<ChunkXZ> chunks = new HashSet<>();
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
    private int proceeded = 0;
    private double percent = 0;
    private Iterator<ChunkXZ> chunkIterator;
    private Iterator<Filler> wallIterator;
    private Iterator<Filler> spotIterator;
    private boolean wallGenerated = false;
    private boolean wallGenQueued = false;

    //performance
    private long lastNotifyTime;
    private long nextLaunchTime;
    private int skip;

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
        int cThickness = WorldUtil.calcChunk(endWallCoord) - WorldUtil.calcChunk(startWallCoord);

        // fill gen queue
        // +x
        for (int cz = WorldUtil.calcChunk(-startWallCoord); cz <= WorldUtil.calcChunk(endWallCoord); cz++)
            for (int cx = WorldUtil.calcChunk(startWallCoord); cx <= WorldUtil.calcChunk(startWallCoord) + cThickness; cx++)
                chunks.add(new ChunkXZ(cx, cz));
        // +z
        for (int cx = WorldUtil.calcChunk(startWallCoord); cx >= WorldUtil.calcChunk(-endWallCoord); cx--)
            for (int cz = WorldUtil.calcChunk(startWallCoord); cz <= WorldUtil.calcChunk(startWallCoord) + cThickness; cz++)
                chunks.add(new ChunkXZ(cx, cz));
        // -x
        for (int cz = WorldUtil.calcChunk(startWallCoord); cz >= WorldUtil.calcChunk(-endWallCoord); cz--)
            for (int cx = WorldUtil.calcChunk(-startWallCoord); cx >= WorldUtil.calcChunk(-startWallCoord) - cThickness; cx--)
                chunks.add(new ChunkXZ(cx, cz));
        // -z
        for (int cx = WorldUtil.calcChunk(-startWallCoord); cx <= WorldUtil.calcChunk(endWallCoord); cx++)
            for (int cz = WorldUtil.calcChunk(-startWallCoord); cz >= WorldUtil.calcChunk(-startWallCoord) - cThickness; cz--)
                chunks.add(new ChunkXZ(cx, cz));

        //launch job
        percent = 0;
        proceeded = 0;
        nextLaunchTime = System.currentTimeMillis();
        skip = 0;
        chunkIterator = chunks.iterator();
        scheduleTick(this::generateChunksTick);
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
        proceeded = 0;
        nextLaunchTime = System.currentTimeMillis();
        skip = 0;
        wallIterator = wallParts.listIterator();
        scheduleTick(this::generateWallTick);
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
        proceeded = 0;
        percent = 0;
        nextLaunchTime = System.currentTimeMillis();
        skip = 0;
        wallIterator = wallParts.listIterator();
        scheduleTick(this::rollbackWallTick);

        // balconies fix
        spotBalconies.forEach(filler -> filler.setMaterial(Material.AIR).fill());
        spotBalconies.clear();
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
        skip = 0;
        scheduleTick(this::generateSpotsTick);
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
        skip = 0;
        scheduleTick(this::rollbackSpotsTick);
    }

    private void generateChunksTick() {
        long time = System.currentTimeMillis();
        // progress notification
        if (time - lastNotifyTime > NOTIFY_INTERVAL) {
            Bukkit.broadcastMessage(String.format(Messages.WALL_CHUNK_GEN_PROGRESS, (int) (percent * 100)));
            lastNotifyTime = time;
        }
        // check skip
        if (time < nextLaunchTime) {
            scheduleTick(this::generateChunksTick);
            return;
        }
        // proceed
        if (chunkIterator.hasNext()) {
            // generate chunk
            chunkIterator.next().getChunk(world).load(true);
            scheduleTick(this::generateChunksTick);
            percent = (double) ++proceeded / chunks.size();
            nextLaunchTime = System.currentTimeMillis() + 50;
        } else {
            // prune queue and finish task
            percent = 1;
            chunks.clear();
            LoggerUtil.info(INFO_GENERATE_CHUNKS_END);
            generateWallInit();
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
        if (time < nextLaunchTime || skip > 0) {
            skip--;
            scheduleTick(this::generateWallTick);
            return;
        }
        //proceed
        if (wallIterator.hasNext()) {
            wallIterator.next().fill();
            scheduleTick(this::generateWallTick);
            percent = (double) ++proceeded / wallParts.size();
            long delta = System.currentTimeMillis() - time;
            nextLaunchTime = time + 1000 + delta;
            skip = 20 + (int) delta / 50;
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
        if (time < nextLaunchTime || skip > 0) {
            skip--;
            scheduleTick(this::rollbackWallTick);
            return;
        }
        //proceed
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
            scheduleTick(this::rollbackWallTick);
            percent = (double) ++proceeded / wallParts.size();
            long delta = System.currentTimeMillis() - time;
            nextLaunchTime = time + 1000 + delta;
            skip = 20 + (int) delta / 50;
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
            scheduleTick(this::generateSpotsTick);
            return;
        }
        if (--skip > 0) {
            scheduleTick(this::generateSpotsTick);
            return;
        }
        int currentSpot = 0;
        while (currentSpot < spotLimiter && spotIterator.hasNext()) {
            currentSpot++;
            spotIterator.next().fill();
        }
        if (spotIterator.hasNext()) {
            skip = 20;
            scheduleTick(this::generateSpotsTick);
        } else {
            spotBalconies.forEach(Filler::fill);
            LoggerUtil.info(INFO_GENERATE_SPOTS_END);
        }
    }

    private void rollbackSpotsTick() {
        if (--skip > 0) {
            scheduleTick(this::generateSpotsTick);
            return;
        }
        int currentSpot = 0;
        while (currentSpot < spotLimiter && spotIterator.hasNext()) {
            currentSpot++;
            spotIterator.next().setMaterial(Material.BEDROCK).fill();
        }
        if (spotIterator.hasNext()) {
            skip = 20;
            scheduleTick(this::generateSpotsTick);
        } else {
            spotBalconies.forEach(filler -> filler.setMaterial(Material.AIR).fill());
            LoggerUtil.info(INFO_ROLLBACK_SPOTS_END);
            spots.clear();
            spotBalconies.clear();
            Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
        }
    }

    // ======================================== //

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

    private static void scheduleTick(Runnable task) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(Engine.getInstance(), task, 1);
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

    // Performance fix: world.getChunkAt loads chunk before returning it.
    private static class ChunkXZ {
        int x;
        int z;

        private ChunkXZ(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private Chunk getChunk(World world) {
            return world.getChunkAt(x, z);
        }
    }
}
