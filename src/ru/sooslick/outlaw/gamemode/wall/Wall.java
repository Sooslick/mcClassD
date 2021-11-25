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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Wall {
    // strings
    private static final String DEBUG_LIMITER = "Wall limiter is %s. Expected volume: %s";
    private static final String DEBUG_MIN_HEIGHT = "Wall starts from y = ";
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
    private static final int minY = defineMinY();
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
    private boolean wallRlbQueued = false;

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
        size = wallCfg.playzoneSize;
        thickness = wallCfg.wallThickness;
        wallGenQueued = true;

        int halfSize = size / 2;
        startWallCoord = halfSize + 1;
        endWallCoord = startWallCoord + thickness - 1;

        boolean reqRebuild = wallGenerated && size != oldSize;
        if (wallGenerated) {
            if (reqRebuild) {
                wallRlbQueued = true;
                LoggerUtil.warn(Messages.UNPLAYABLE_WORLD_WARNING);
            }
            rollbackSpotsInit();
        } else
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
        wallRlbQueued = true;
        LoggerUtil.warn(Messages.UNPLAYABLE_WORLD_WARNING);
        rollbackSpotsInit();
    }

    private void generateChunksInit() {
        LoggerUtil.info(INFO_GENERATE_CHUNKS);
        // fill gen queue
        // +x
        for (int cz = WorldUtil.calcChunk(-startWallCoord); cz <= WorldUtil.calcChunk(endWallCoord); cz++)
            chunks.add(new ChunkXZ(startWallCoord, cz));
        // +z
        for (int cx = WorldUtil.calcChunk(startWallCoord); cx >= WorldUtil.calcChunk(-endWallCoord); cx--)
            chunks.add(new ChunkXZ(cx, startWallCoord));
        // -x
        for (int cz = WorldUtil.calcChunk(startWallCoord); cz >= WorldUtil.calcChunk(-endWallCoord); cz--)
            chunks.add(new ChunkXZ(-startWallCoord, cz));
        // -z
        for (int cx = WorldUtil.calcChunk(-startWallCoord); cx <= WorldUtil.calcChunk(endWallCoord); cx++)
            chunks.add(new ChunkXZ(cx, -startWallCoord));

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
        int h = maxY - minY + 1;
        int limiter = Cfg.blocksPerSecondLimit / h;
        if (limiter < 2) {
            limiter = 2;
            Cfg.blocksPerSecondLimit = h * 2 + 1;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        } else if (limiter > 256)
            limiter = 256;          // max 16 chunks per second sounds ok I guess?
        LoggerUtil.debug(String.format(DEBUG_LIMITER, limiter, limiter * 256));

        // fill wall queue
        // +x
        for (int z = -startWallCoord; z <= endWallCoord; z += limiter) {
            int endZ = Math.min(z + limiter - 1, endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startWallCoord).setEndX(startWallCoord)
                    .setStartZ(z).setEndZ(endZ));
        }
        // +z
        for (int x = startWallCoord; x >= -endWallCoord; x -= limiter) {
            int startX = Math.max(x - limiter + 1, -endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startX).setEndX(x)
                    .setStartZ(startWallCoord).setEndZ(startWallCoord));
        }
        // -x
        for (int z = startWallCoord; z >= -endWallCoord; z -= limiter) {
            int startZ = Math.max(z - limiter + 1, -endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(-startWallCoord).setEndX(-startWallCoord)
                    .setStartZ(startZ).setEndZ(z));
        }
        // -z
        for (int x = -startWallCoord; x <= endWallCoord; x += limiter) {
            int endX = Math.min(x + limiter - 1, endWallCoord);
            wallParts.add(getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(x).setEndX(endX)
                    .setStartZ(-startWallCoord).setEndZ(-startWallCoord));
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
        int spotVolume = thickness * (spotSize + 2) * (spotSize + 2);
        spotLimiter = (int) Math.floor((double) Cfg.blocksPerSecondLimit / spotVolume / 2);
        if (spotLimiter < 1) {
            spotLimiter = 1;
            Cfg.blocksPerSecondLimit = spotVolume + 1;
            LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL + Cfg.blocksPerSecondLimit);
        } else if (spotLimiter > 4)
            spotLimiter = 4;           // pretty hard to load chunks chaotic

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
            Filler spotFiller = getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startWallCoord).setEndX(endWallCoord)
                    .setStartY(groundY - 1).setEndY(groundY + spotSize)
                    .setStartZ(startZ - 1).setEndZ(startZ + spotSize);
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
            Filler spotFiller = getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startX - 1).setEndX(startX + spotSize)
                    .setStartY(groundY - 1).setEndY(groundY + spotSize)
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
            Filler spotFiller = getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(-endWallCoord).setEndX(-startWallCoord)
                    .setStartY(groundY - 1).setEndY(groundY + spotSize)
                    .setStartZ(startZ - 1).setEndZ(startZ + spotSize);
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
            Filler spotFiller = getTemplateFiller(world, Material.BEDROCK)
                    .setStartX(startX - 1).setEndX(startX + spotSize)
                    .setStartY(groundY - 1).setEndY(groundY + spotSize)
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
            Bukkit.broadcastMessage(String.format(Messages.WALL_GEN_ROLLBACK_PROGRESS, 100));
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
            Filler spotBorder = spotIterator.next();
            spotBorder.fill();
            Filler spotFiller = spotBorder.copy().setMaterial(Material.OBSIDIAN);
            if (Math.abs(spotFiller.getStartX()) >= size / 2) {
                spotFiller.setStartZ(spotFiller.getStartZ() + 1);
                spotFiller.setEndZ(spotFiller.getEndZ() - 1);
            } else {
                spotFiller.setStartX(spotFiller.getStartX() + 1);
                spotFiller.setEndX(spotFiller.getEndX() - 1);
            }
            spotFiller.setStartY(spotFiller.getStartY() + 1);
            spotFiller.setEndY(spotFiller.getEndY() - 1);
            spotFiller.fill();
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
            Filler spotFiller = spotIterator.next();
            spotFiller.fill();
            if (Math.abs(spotFiller.getStartX()) >= size / 2) {
                if (spotFiller.getStartX() > 0)
                    spotFiller.setStartX(spotFiller.getStartX() + 1);
                else
                    spotFiller.setEndX(spotFiller.getEndX() - 1);
            } else {
                if (spotFiller.getStartZ() > 0)
                    spotFiller.setStartZ(spotFiller.getStartZ() + 1);
                else
                    spotFiller.setEndZ(spotFiller.getEndZ() - 1);
            }
            spotFiller.setMaterial(Material.AIR).fill();
        }
        if (spotIterator.hasNext()) {
            skip = 20;
            scheduleTick(this::generateSpotsTick);
        } else {
            spotBalconies.forEach(filler -> filler.setMaterial(Material.AIR).fill());
            LoggerUtil.info(INFO_ROLLBACK_SPOTS_END);
            spots.clear();
            spotBalconies.clear();
            if (wallRlbQueued)
                rollbackWallInit();
            else
                Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
        }
    }

    // ======================================== //

    @SuppressWarnings("SameParameterValue")
    private static Filler getTemplateFiller(World world, Material mat) {
        return new Filler(world, mat)
                .setStartY(minY)
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
        if (groundLevel < 0)
            groundLevel = 0;
        else if (groundLevel >= maxY - 32)
            groundLevel = maxY - 32;
        switch (level) {
            case -1:
                return CommonUtil.random.nextInt(groundLevel - deadzone - 2 - minY) + 2 + minY;
            case 1:
                return CommonUtil.random.nextInt(maxY - groundLevel - deadzone - 1) + groundLevel;
            default:
                return groundLevel;
        }
    }

    // weird solution to keep compatibility with both 1.17 and 1.18.
    private static int defineMinY() {
        int minY = 0;
        try {
            Matcher m = Pattern.compile("1\\.(\\d+)").matcher(Bukkit.getServer().getVersion());
            if (m.find()) {
                int v = Integer.parseInt(m.group(1));
                if (v >= 18)
                    minY = -64;
            }
        } catch (Exception ignored) {
        }
        LoggerUtil.debug(DEBUG_MIN_HEIGHT + minY);
        return minY;
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
