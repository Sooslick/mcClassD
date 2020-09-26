package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedList;
import java.util.logging.Logger;

public class Wall {
    private static int generatorTimerId;
    private static LinkedList<Integer> spotPositions;
    private static int groundMax;
    private static int airMax;
    private static int undergroundMax;
    private static int size;
    private static int halfSize;
    private static int spotSize;
    private static int spotHalfSize;
    private static int side;                        //current side of square
    private static int currentBlock;                //current block of side
    private static int genState;                    //generator state
    private static int limiter = 256;
    public static int startPosY;                    //todo private
    private static int endPosY;
    private static World w;
    private static Logger log;

    private final static Runnable generatorTick = () -> {
        switch (genState) {
            case 0:             //generate walls
                boolean gotoNext = false;
                int startPosX = -halfSize + currentBlock;
                int endPosX = startPosX + limiter;
                if (endPosX > halfSize) {
                    endPosX = halfSize;
                    gotoNext = true;
                }
                switch (side) {
                    case 0:                                         //x -> x, y -> +z
                        for (int i = startPosX; i <= endPosX; i++)      //x
                            for (int j = startPosY; j < endPosY; j++)  //z
                                for (int k = 0; k < 256; k++)          //y
                                    w.getBlockAt(i, k, j).setType(Material.BEDROCK);
                        break;
                    case 1:                                         //x -> z, y -> +x
                        for (int i = startPosX; i <= endPosX; i++)      //z
                            for (int j = startPosY; j < endPosY; j++)  //x
                                for (int k = 0; k < 256; k++)          //y
                                    w.getBlockAt(j, k, i).setType(Material.BEDROCK);
                        break;
                    case 2:                                             //x -> x, y -> -z
                        for (int i = startPosX; i <= endPosX; i++)          //x
                            for (int j = -startPosY; j > -endPosY; j--)    //z
                                for (int k = 0; k < 256; k++)              //y
                                    w.getBlockAt(i, k, j).setType(Material.BEDROCK);
                        break;
                    case 3:                                             //x -> z, y -> -x
                        for (int i = startPosX; i <= endPosX; i++)          //z
                            for (int j = -startPosY; j > -endPosY; j--)    //x
                                for (int k = 0; k < 256; k++)              //y
                                    w.getBlockAt(j, k, i).setType(Material.BEDROCK);
                        break;
                    case 4:
                        genState++;
                        gotoNext = false;
                        currentBlock = 0;
                        side = 0;
                        log.info("built wall, launch spot processing");
                        break;
                }
                log.info("Gen wall, side " + side + ", block " + currentBlock);
                if (gotoNext) {
                    side++;
                    currentBlock = 0;
                } else {
                    currentBlock += limiter;
                }
                break;
            case 1:             //generate spots
                switch (side) {
                    case 0:             //x -> x, y -> +z
                        for (int q = 0; q < groundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            Block b = w.getBlockAt(baseX, 0, halfSize);
                            w.loadChunk(b.getChunk());
                            int baseH = w.getHighestBlockYAt(b.getLocation()) + 2;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = startPosY; j < endPosY; j++)                               //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < undergroundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(56);
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = startPosY; j < endPosY; j++)                               //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < airMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(120) + 80;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = startPosY; j < endPosY; j++)                               //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        side++;
                        break;
                    case 1:                 //x -> z, y -> +x
                        for (int q = 0; q < groundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            Block b = w.getBlockAt(baseX, 0, halfSize);
                            w.loadChunk(b.getChunk());
                            int baseH = w.getHighestBlockYAt(b.getLocation()) + 2;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = startPosY; j < endPosY; j++)                               //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < undergroundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(56);
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = startPosY; j < endPosY; j++)                               //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < airMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(120) + 80;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = startPosY; j < endPosY; j++)                               //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        side++;
                        break;
                    case 2:                                         //x -> x, y -> -z
                        for (int q = 0; q < groundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            Block b = w.getBlockAt(baseX, 0, halfSize);
                            w.loadChunk(b.getChunk());
                            int baseH = w.getHighestBlockYAt(b.getLocation()) + 2;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = -startPosY; j > -endPosY; j--)                               //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < undergroundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(56);
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = -startPosY; j > -endPosY; j--)                                //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < airMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(120) + 80;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //x
                                for (int j = -startPosY; j > -endPosY; j--)                                //z
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(i, k, j).setType(Material.OBSIDIAN);
                        }
                        side++;
                        break;
                    case 3:         //x -> z, y -> -x
                        for (int q = 0; q < groundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            Block b = w.getBlockAt(baseX, 0, halfSize);
                            w.loadChunk(b.getChunk());
                            int baseH = w.getHighestBlockYAt(b.getLocation()) + 2;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = -startPosY; j > -endPosY; j--)                                //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < undergroundMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(56);
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = -startPosY; j > -endPosY; j--)                               //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        for (int q = 0; q < airMax; q++) {
                            int baseX = spotPositions.get(0);
                            spotPositions.remove(0);
                            int baseH = Util.random.nextInt(120) + 80;
                            for (int i = baseX - spotHalfSize; i < baseX + spotHalfSize; i++)               //z
                                for (int j = -startPosY; j > -endPosY; j--)                               //x
                                    for (int k = baseH - spotHalfSize; k < baseH + spotHalfSize; k++)     //y
                                        w.getBlockAt(j, k, i).setType(Material.OBSIDIAN);
                        }
                        side++;
                        break;
                    case 4:
                        genState++;
                        log.info("generated escape spots, finishing...");
                        break;
                }
                break;
            case 2:             //generate corners
                for (int i = startPosY; i < endPosY; i++)
                    for (int j = startPosY; j < endPosY; j++)
                        for (int k = 0; k < 256; k++)
                            w.getBlockAt(i, k, j).setType(Material.BEDROCK);
                for (int i = startPosY; i < endPosY; i++)
                    for (int j = startPosY; j < endPosY; j++)
                        for (int k = 0; k < 256; k++)
                            w.getBlockAt(-i, k, j).setType(Material.BEDROCK);
                for (int i = startPosY; i < endPosY; i++)
                    for (int j = startPosY; j < endPosY; j++)
                        for (int k = 0; k < 256; k++)
                            w.getBlockAt(-i, k, -j).setType(Material.BEDROCK);
                for (int i = startPosY; i < endPosY; i++)
                    for (int j = startPosY; j < endPosY; j++)
                        for (int k = 0; k < 256; k++)
                            w.getBlockAt(i, k, -j).setType(Material.BEDROCK);
                genState++;
                Bukkit.getScheduler().cancelTask(generatorTimerId);
                Bukkit.broadcastMessage("ready to next game");
                break;
        }
    };

    //todo generate spots after startgame
    //todo: refactor this piece of shit

    public static void generate(Engine engine) {
        //stop previous generator if it still working, clear
        Bukkit.getScheduler().cancelTask(generatorTimerId);
        size = Cfg.playzoneSize;
        halfSize = size / 2;
        startPosY = halfSize + 1;
        endPosY = startPosY + Cfg.wallThickness;
        spotSize = Cfg.spotSize;
        spotHalfSize = spotSize / 2;
        side = 0;
        currentBlock = 0;
        genState = 0;
        groundMax = Cfg.groundSpotDensity;
        undergroundMax = Cfg.undergroundSpotDensity;
        airMax = Cfg.airSpotDensity;
        w = Bukkit.getWorlds().get(0);

        //pre-generate spots
        spotPositions = new LinkedList<>();
        int total = (Cfg.airSpotDensity + Cfg.groundSpotDensity + Cfg.undergroundSpotDensity) * 4;
        for (int i = 0; i < total; i++) {
            spotPositions.add(Util.random.nextInt(size) - halfSize);
        }

        //launch
        log = Bukkit.getLogger();
        generatorTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(engine, generatorTick, 1, 20);
        log.info("Wall generator launched");
    }
}
