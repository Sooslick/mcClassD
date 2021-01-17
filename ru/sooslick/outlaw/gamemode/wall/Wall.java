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
    private static final String BROADCAST_BUILD_PREDICTION = "§cPlease wait until the Wall is rebuilt. Estimated wait time: ";
    private static final String DEBUG_KILLED = "All tasks completed, remove Wall from scheduler";
    private static final String DEBUG_LIMITER = "Wall limiter is %s. Expected volume: %s";
    private static final String DEBUG_TASK_FINISHED = "Init new Wall Task ";
    private static final String DEBUG_TASK_IGNORED = "Task %s not queued due to killed state";
    private static final String DEBUG_TASK_QUEUED = "Queued task ";
    private static final String DEBUG_TASK_SUSPICIOUS = "Suspicious task state, marked as completed";
    private static final String DEBUG_WALL_ROLLBACK = "The Wall will be rebuilt completely";
    private static final String WARN_BUILD_LIMIT_TOO_SMALL = "blocksPerSecondLimit value too small, fixed it";

    private static int queueTaskId;
    private static WallGameModeConfig wallCfg;
    private static LinkedList<Integer> spotPositions;
    private static int groundCurr, airCurr, undergroundCurr;
    private static int oldSize, oldThickness;       //rollback variables
    private static int size, halfSize, spotSize;
    private static int side;                        //current side of square
    private static int currentBlock;                //current block of side
    private static int limiter;
    private static int startWallCoord;
    private static int endWallCoord;
    private static World world;
    private static LinkedList<Task> taskQueue;
    private static LinkedList<Filler> rollbackWallFillers;
    private static LinkedList<Filler> rollbackSpotFillers;
    private static Task currentTask;
    private static boolean taskFinished;
    private static boolean killed;

    private static final Runnable queueTick = () -> {
        //if gamemode unloaded and all tasks completed
        if (killed && taskFinished && taskQueue.isEmpty()) {
            Bukkit.getScheduler().cancelTask(queueTaskId);
            LoggerUtil.debug(DEBUG_KILLED);
            return;
        }

        //nothing to run, try to get the next task
        if (taskFinished) {
            if (!taskQueue.isEmpty()) {
                currentTask = taskQueue.getFirst();
                currentTask.runInit();
                taskFinished = false;
                taskQueue.removeFirst();
                LoggerUtil.debug(DEBUG_TASK_FINISHED + currentTask.toString());
            }
            //otherwise just wait
        }
        //run task's tick
        else {
            //unreachable branch, safety check
            if (currentTask == null) {
                taskFinished = true;
                LoggerUtil.debug(DEBUG_TASK_SUSPICIOUS);
            } else {
                currentTask.runTick();
            }
        }
    };

    //disable constructor for utility class
    private Wall() {
    }

    public static void initWith(WallGameModeConfig cfg) {
        wallCfg = cfg;
        taskQueue = new LinkedList<>();
        rollbackWallFillers = new LinkedList<>();
        rollbackSpotFillers = new LinkedList<>();
        killed = false;
        currentTask = null;
        taskFinished = true;
        oldSize = 0;
        oldThickness = 0;
        queueTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), queueTick, 20, 20);
    }

    public static void prepareWall() {
        if (wallCfg.wallThickness != oldThickness || wallCfg.playzoneSize != oldSize) {
            LoggerUtil.debug(DEBUG_WALL_ROLLBACK);
            oldSize = wallCfg.playzoneSize;
            oldThickness = wallCfg.wallThickness;
            if (!rollbackWallFillers.isEmpty())
                queueTask(Task.ROLLBACK_WALL);
            queueTask(Task.GENERATE_WALL);
        } else {
            queueTask(Task.ROLLBACK_SPOTS);
        }
    }

    public static void prepareSpots() {
        queueTask(Task.GENERATE_SPOTS);
    }

    public static void rollback() {
        if (!rollbackSpotFillers.isEmpty())
            queueTask(Task.ROLLBACK_SPOTS);
        queueTask(Task.ROLLBACK_WALL);
        killed = true;
        //instantly stop generate tasks
        if (currentTask == Task.GENERATE_WALL || currentTask == Task.GENERATE_SPOTS) {
            taskFinished = true;
        }
    }

    private static void queueTask(Task task) {
        if (killed) {
            LoggerUtil.debug(String.format(DEBUG_TASK_IGNORED, task));
            return;
        }
        taskQueue.add(task);
        LoggerUtil.debug(DEBUG_TASK_QUEUED + task.toString());
    }

    private static Filler getSideBasedFiller(int side, int from, int to) {
        Filler f = new Filler().setWorld(world);
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
                b = world.getBlockAt(startWallCoord - 1, 0, center);
                break;
            case 1:                 //+z
                b = world.getBlockAt(center, 0, startWallCoord - 1);
                break;
            case 2:                 //-x
                b = world.getBlockAt(-startWallCoord + 1, 0, center);
                break;
            case 3:                 //-z
                b = world.getBlockAt(center, 0, -startWallCoord + 1);
                break;
            default:
                return 65;
        }
        world.loadChunk(b.getChunk());
        return world.getHighestBlockAt(b.getLocation()).getY() + 1;
    }

    private static int getAirLevel(int side, int center) {
        int groundLevel = getGroundLevel(side, center);
        return CommonUtil.random.nextInt(240 - groundLevel) + groundLevel + spotSize;
    }

    private static int getUndergroundLevel(int side, int center) {
        int groundLevel = getGroundLevel(side, center);
        return CommonUtil.random.nextInt(groundLevel - spotSize) + spotSize;
    }

    private enum Task {
        GENERATE_WALL(
                //INIT RUNNABLE
                () -> {
                    size = wallCfg.playzoneSize;
                    halfSize = size / 2;
                    startWallCoord = halfSize + 1;
                    endWallCoord = startWallCoord + wallCfg.wallThickness - 1;
                    side = 0;
                    currentBlock = -startWallCoord;         //from -start to +end
                    limiter = Cfg.blocksPerSecondLimit / 256 / wallCfg.wallThickness;
                    LoggerUtil.debug(String.format(DEBUG_LIMITER, limiter, limiter*256*wallCfg.wallThickness));
                    if (limiter < 2) {
                        limiter = 2;
                        Cfg.blocksPerSecondLimit = wallCfg.wallThickness * 512;
                        LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL);
                    }
                    world = Bukkit.getWorlds().get(0);
                    String duration = CommonUtil.formatDuration(Duration.ofSeconds(size * 4 / limiter));
                    Bukkit.broadcastMessage(BROADCAST_BUILD_PREDICTION + duration);
                },
                //TICK RUNNABLE
                () -> {
                    int from = currentBlock;
                    int to = currentBlock + limiter - 1;
                    if (to > endWallCoord)
                        to = endWallCoord;
                    Filler f = getSideBasedFiller(side, from, to)
                            .setStartY(0)
                            .setEndY(255)
                            .setMaterial(Material.BEDROCK);
                    if (f.fill()) {
                        rollbackWallFillers.add(f);
                        currentBlock += limiter;
                        if (currentBlock >= endWallCoord) {
                            currentBlock = -startWallCoord;
                            side++;
                            if (side > 3) {
                                taskFinished = true;
                                if (Engine.getInstance().getGameState() == GameState.IDLE)
                                    Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
                            }
                        }
                    }
                }),
        ROLLBACK_WALL(
                //INIT RUNNABLE
                () -> {
                    if (rollbackWallFillers.isEmpty())
                        return;
                    int size = rollbackWallFillers.get(0).size();
                    limiter = Cfg.blocksPerSecondLimit / size;
                    if (limiter < 1) {
                        limiter = 1;
                        Cfg.blocksPerSecondLimit = size;
                        LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL);
                    }
                    String duration = CommonUtil.formatDuration(Duration.ofSeconds(rollbackWallFillers.size() * 2 / limiter));
                    Bukkit.broadcastMessage(BROADCAST_BUILD_PREDICTION + duration);
                },
                //TICK RUNNABLE
                () -> {
                    for (int i = 0; i < limiter; i++) {
                        if (rollbackWallFillers.isEmpty()) {
                            taskFinished = true;
                            return;
                        }
                        Filler f = rollbackWallFillers.getFirst();
                        //todo normalize
                        f.setMaterial(Material.AIR).fill();
                        rollbackWallFillers.removeFirst();
                    }
                }
        ),
        GENERATE_SPOTS(
                //INIT RUNNABLE
                () -> {
                    spotSize = wallCfg.spotSize;
                    side = 0;
                    groundCurr = 0;
                    undergroundCurr = 0;
                    airCurr = 0;
                    limiter = Cfg.blocksPerSecondLimit / ((spotSize*2+1)*(spotSize*2+1)*wallCfg.wallThickness);
                    if (limiter < 1) {
                        limiter = 1;
                        Cfg.blocksPerSecondLimit = ((spotSize*2+1)*(spotSize*2+1)*wallCfg.wallThickness);
                        LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL);
                    } else if (limiter > 5)
                        //force limiter due to hard chunk loads
                        limiter = 5;

                    //pre-generate spots
                    spotPositions = new LinkedList<>();
                    int total = (wallCfg.airSpotQty + wallCfg.groundSpotQty + wallCfg.undergroundSpotQty) * 4;
                    for (int i = 0; i < total; i++) {
                        spotPositions.add(CommonUtil.random.nextInt(size) - halfSize);
                    }
                },
                //TICK RUNNABLE
                () -> {
                    for (int i = 0; i < limiter; i++) {
                        //break condition
                        if (spotPositions.isEmpty() || taskFinished) {
                            taskFinished = true;
                            return;
                        }
                        int center = spotPositions.getFirst();
                        Filler f = getSideBasedFiller(side, center - spotSize, center + spotSize).setMaterial(Material.OBSIDIAN);
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
                                    taskFinished = true;
                                }
                            }
                        }
                        rollbackSpotFillers.add(f);
                        spotPositions.removeFirst();
                    }
                }
        ),
        ROLLBACK_SPOTS(
                //INIT RUNNABLE
                () -> {
                    if (rollbackSpotFillers.isEmpty())
                        return;
                    limiter = Cfg.blocksPerSecondLimit / rollbackSpotFillers.get(0).size();
                    if (limiter < 1) {
                        limiter = 1;
                        Cfg.blocksPerSecondLimit = rollbackSpotFillers.get(0).size();
                        LoggerUtil.warn(WARN_BUILD_LIMIT_TOO_SMALL);
                    } else if (limiter > 5)
                        //force limit due to spamming with chunk loading
                        limiter = 5;
                },
                //TICK RUNNABLE
                () -> {
                    for (int i = 0; i < limiter; i++) {
                        if (rollbackSpotFillers.isEmpty()) {
                            taskFinished = true;
                            return;
                        }
                        Filler f = rollbackSpotFillers.getFirst();
                        f.setMaterial(Material.BEDROCK).fill();
                        rollbackSpotFillers.removeFirst();
                    }
                }
        );

        private final Runnable init;
        private final Runnable tick;

        private Task(Runnable init, Runnable tick) {
            this.init = init;
            this.tick = tick;
        }

        private void runInit() {
            init.run();
        }

        private void runTick() {
            tick.run();
        }
    }
}
