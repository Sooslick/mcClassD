package ru.sooslick.outlaw.gamemode.evacuation;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.util.Filler;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.List;

@SuppressWarnings("unused")
public class EvacuationBase implements GameModeBase {
    private EvacuationConfig evaCfg;
    private World world;

    //core
    private int timeLeft;
    private Filler vehicle;
    private Runnable stage;
    private int jobId;
    private Score score;

    //vehicle
    private Location evacPoint;
    private int stopHeight;
    private int jobPeriod;
    private int currentHeight;

    //world border
    WorldBorder wb;
    private double cx;
    private double cz;
    private double dx;
    private double dz;

    //placeholder runnable for landing stage
    private final Runnable DULL = () -> {
    };

    private final Runnable ZONE_ALIGN = () -> {
        if (wb.getSize() > evaCfg.cordonZoneSize) {
            cx += dx;
            cz += dz;
        } else {
            cx = evacPoint.getX();
            cz = evacPoint.getZ();
            Bukkit.getScheduler().cancelTask(jobId);
        }
        wb.setCenter(cx, cz);
    };

    private final Runnable EVAC = () -> {
        score.setScore((int) WorldUtil.distance2d(Engine.getInstance().getOutlaw().getLocation(), evacPoint));
        //fix cross-world compass
        Engine.getInstance().getOutlaw().getPlayer().setCompassTarget(evacPoint);

        //check escape
        int bx = evacPoint.getBlockX();
        int bz = evacPoint.getBlockZ();
        Engine e = Engine.getInstance();
        Location outlawLoc = e.getOutlaw().getLocation();
        if (outlawLoc.getWorld() == world) {
            if ((outlawLoc.getBlockX() >= bx-2) && (outlawLoc.getBlockX() <= bx+2) &&
                    (outlawLoc.getBlockZ() >= bz-2) && (outlawLoc.getBlockZ() <= bz+2) &&
                    (outlawLoc.getBlockY() >= currentHeight) && (outlawLoc.getBlockY() <= currentHeight + 3))
                e.triggerEndgame(true);
        }
    };

    //calculated amount of ticks between landing
    private final Runnable LANDING = () -> {
        score.setScore(timeLeft / 20);
        //fix cross-world compass
        Engine.getInstance().getOutlaw().getPlayer().setCompassTarget(evacPoint);

        boolean free = true;
        int bx = evacPoint.getBlockX();
        int bz = evacPoint.getBlockZ();
        int ch = currentHeight - 1;
        for (int x = bx - 2; x <= bx + 2; x++)
            for (int z = bz - 2; z <= bz + 2; z++)
                if (world.getBlockAt(x, ch, z).getType() != Material.AIR) {
                    free = false;
                    break;
                }
        if (free) {
            currentHeight = ch;
            if (currentHeight >= stopHeight) {
                vehicle.setMaterial(Material.AIR).fill();
                vehicle.setStartY(currentHeight).setEndY(currentHeight + 2).setMaterial(Material.BEDROCK).fill();
            }
        }

        if ((timeLeft-= jobPeriod) <= 0) {
            stage = EVAC;
            timeLeft = evaCfg.cordonTime;
            Bukkit.getScheduler().cancelTask(jobId);
            vehicle.setMaterial(Material.AIR).fill();
            vehicle.setStartX(bx-2).setEndX(bx+2)
                    .setStartZ(bz-2).setEndZ(bz+2)
                    .setStartY(currentHeight).setEndY(currentHeight)
                    .setMaterial(Material.BEDROCK)
                    .fill();

            //launch worldborder
            wb = world.getWorldBorder();
            wb.setSize(evaCfg.cordonZoneSize, evaCfg.cordonTime);
            cx = 0;
            cz = 0;
            dx = evacPoint.getX() / (timeLeft * 20);
            dz = evacPoint.getZ() / (timeLeft * 20);
            jobId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), ZONE_ALIGN, 1, 1);

            //other worldborders without align
            Bukkit.getWorlds().forEach(w -> {
                if (world == w)
                    return;
                w.getWorldBorder().setSize(evaCfg.cordonZoneSize, evaCfg.cordonTime);
            });

            Bukkit.broadcastMessage(Messages.EVAC_VEHICLE_READY);
        }
    };

    //one second tick for wait stage
    private final Runnable WAIT = () -> {
        score.setScore(timeLeft + evaCfg.landingTime);
        if (--timeLeft <= 0) {
            stage = DULL;

            //select evac point
            evacPoint = WorldUtil.getRandomLocation((evaCfg.playzoneSize / 2) - 2);
            int bx = evacPoint.getBlockX();
            int bz = evacPoint.getBlockZ();
            currentHeight = world.getMaxHeight() - 4;

            //calculate stopH
            stopHeight = 66;
            for (int x = bx - 2; x <= bx + 2; x++)
                for (int z = bz - 2; z <= bz + 2; z++)
                    for (int y = currentHeight; y > Math.max(66, stopHeight); y--)
                        if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                            stopHeight = y + 3;
                            break;
                        }

            //calculate landing frequency
            timeLeft = evaCfg.landingTime * 20;
            int distance = currentHeight - stopHeight;
            jobPeriod = timeLeft / distance;

            //create "evac vehicle"
            vehicle = new Filler().setWorld(world)
                    .setStartX(bx-1).setEndX(bx+1)
                    .setStartZ(bz-1).setEndZ(bz+1)
                    .setStartY(currentHeight).setEndY(currentHeight+2)
                    .setMaterial(Material.BEDROCK);
            vehicle.fill();

            //give compass to victim
            Player victim = Engine.getInstance().getOutlaw().getPlayer();
            victim.getInventory().addItem(new ItemStack(Material.COMPASS));
            victim.setCompassTarget(evacPoint);

            Bukkit.broadcastMessage(String.format(Messages.EVAC_VEHICLE_ANNOUNCE, evacPoint.getBlockX(), evacPoint.getBlockZ()));
            victim.sendMessage(Messages.EVAC_COMPASS);

            //run landing job
            jobId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), LANDING, jobPeriod, jobPeriod);
            LoggerUtil.info("Stop height " + stopHeight);
            LoggerUtil.info("Fall period is " + jobPeriod);
        }
    };

    public EvacuationBase() {
        evaCfg = new EvacuationConfig();
        world = Bukkit.getWorlds().get(0);
        jobId = 0;
    }

    @Override
    public void onIdle() {
        List<World> worlds = Bukkit.getWorlds();
        wb = worlds.get(0).getWorldBorder();
        worlds.forEach(w -> {
            WorldBorder cwb = w.getWorldBorder();
            cwb.setCenter(0d, 0d);
            cwb.setSize(evaCfg.playzoneSize);
            cwb.setWarningDistance(10);
        });
        Bukkit.getScheduler().cancelTask(jobId);
        Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
    }

    @Override
    public void onPreStart() {
        cleanup();
    }

    @Override
    public void onGame() {
        timeLeft = evaCfg.waitTime;
        stage = WAIT;

        Engine e = Engine.getInstance();
        Scoreboard sb = e.getScoreboardHolder().getScoreboard();
        if (sb == null) {
            score = null;
            return;
        }

        Objective objective = sb.registerNewObjective("The Wall", "dummy", "The Wall");
        objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        score = objective.getScore(e.getOutlaw().getName());
        score.setScore(evaCfg.waitTime);
    }

    @Override
    public void tick() {
        stage.run();
    }

    @Override
    public void unload() {
        cleanup();
        Bukkit.getScheduler().cancelTask(jobId);
    }

    @Override
    public GameModeConfig getConfig() {
        return evaCfg;
    }

    @Override
    public String getObjective() {
        return Messages.EVAC_OBJECTIVE;
    }

    @Override
    public String getName() {
        return Messages.EVAC_NAME;
    }

    @Override
    public String getDescription() {
        return Messages.EVAC_DESCRIPTION;
    }

    private void cleanup() {
        if (vehicle != null) {
            vehicle.setMaterial(Material.AIR).fill();
        }
    }
}
