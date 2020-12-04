package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import ru.sooslick.outlaw.util.CommonUtil;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.util.LinkedList;

public class Wall {
    private static int generatorTimerId;
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
        LoggerUtil.debug("buildWallTick, side=" + side + ", currentBlock=" + currentBlock);
        int from = currentBlock;
        int to = currentBlock + limiter;
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
                    LoggerUtil.debug("buildWall finished, spotsQueued = " + spotsQueued);
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
        if (groundCurr < Cfg.groundSpotQty) {
            int h = getGroundLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            groundCurr++;
        } else if (airCurr < Cfg.airSpotQty) {
            int h = getAirLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            airCurr++;
        } else if (undergroundCurr < Cfg.undergroundSpotQty) {
            int h = getUndergroundLevel(side, center);
            f.setStartY(h - spotSize).setEndY(h + spotSize).fill();
            if (++undergroundCurr >= Cfg.undergroundSpotQty) {
                groundCurr = 0;
                airCurr = 0;
                undergroundCurr = 0;
                if (++side > 3) {
                    Bukkit.getScheduler().cancelTask(generatorTimerId);
                    LoggerUtil.debug("buildSpots finished");
                }
            }
        }
        spotPositions.removeFirst();
        LoggerUtil.debug("created spot at side " + side + ", center " + center);
    };

    //disable constructor for utility class
    private Wall() {}

    public static void buildWall() {
        //stop previous generator if it still working, clear
        Bukkit.getScheduler().cancelTask(generatorTimerId);
        wallBuilt = false;
        spotsQueued = false;
        size = Cfg.playzoneSize;
        halfSize = size / 2;
        startWallCoord = halfSize + 1;
        endWallCoord = startWallCoord + Cfg.wallThickness - 1;
        side = 0;
        currentBlock = -startWallCoord;         //from -start to +end
        limiter = Cfg.blocksPerSecondLimit / 256 / Cfg.wallThickness;
        if (limiter == 0)
            limiter = 1;
        w = Bukkit.getWorlds().get(0);

        //launch
        generatorTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), buildWallTick, 1, 20);
        LoggerUtil.debug("Reset wall generator and launched buildWallTick");
    }

    public static void launchBuildSpots() {
        spotSize = Cfg.spotSize;
        side = 0;
        groundCurr = 0;
        undergroundCurr = 0;
        airCurr = 0;

        //pre-generate spots
        spotPositions = new LinkedList<>();
        int total = (Cfg.airSpotQty + Cfg.groundSpotQty + Cfg.undergroundSpotQty) * 4;
        for (int i = 0; i < total; i++) {
            spotPositions.add(CommonUtil.random.nextInt(size) - halfSize);
        }

        //launch
        generatorTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), buildSpotTick, 1, 1);
        LoggerUtil.debug("Reset and launched buildSpotTick");
    }

    public static void buildSpots() {
        LoggerUtil.debug("buildSpots queued, wallBuilt = " + wallBuilt);
        if (wallBuilt) {
            launchBuildSpots();
        } else {
            spotsQueued = true;
        }
    }

    private static class Filler {
        private int startX;
        private int startY;
        private int startZ;
        private int endX;
        private int endY;
        private int endZ;
        private Material material;

        public Filler setStartX(int value) {
            startX = value;
            return this;
        }

        public Filler setStartY(int value) {
            startY = value;
            return this;
        }

        public Filler setStartZ(int value) {
            startZ = value;
            return this;
        }

        public Filler setEndX(int value) {
            endX = value;
            return this;
        }

        public Filler setEndY(int value) {
            endY = value;
            return this;
        }

        public Filler setEndZ(int value) {
            endZ = value;
            return this;
        }

        public Filler setMaterial(Material value) {
            material = value;
            return this;
        }

        public boolean fill() {
            //validate size
            int volume = (endX - startX) * (endY - startY) * (endZ - startZ);
            if (Math.abs(volume) > Cfg.blocksPerSecondLimit) {
                LoggerUtil.warn("Wall - fill operation cancelled, blocks limit exceed");
                return false;
            } else if (volume == 0) {
                LoggerUtil.warn("Wall - fill operation cancelled, empty area");
                return false;
            }
            //validate material
            if (material == null) {
                material = Material.AIR;
            }
            //proceed
            for (int x = startX; x <= endX; x++)
                for (int y = startY; y <= endY; y++)
                    for (int z = startZ; z <= endZ; z++)
                        w.getBlockAt(x, y, z).setType(material);
            return true;
        }
    }

    private static Filler getSideBasedFiller(int side, int from, int to) {
        Filler f = new Filler();
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
}
