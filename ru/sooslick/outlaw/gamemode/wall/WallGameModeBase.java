package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.event.HandlerList;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.util.LoggerUtil;

@SuppressWarnings("unused")
public class WallGameModeBase implements GameModeBase {
    private final WallGameModeConfig wallCfg;
    private final Engine engine = Engine.getInstance();

    private final WallEventListener events;
    private boolean hunterAlert;
    private int halfSize;
    private int escapeArea;
    private Score score;

    private final Runnable alertHunter = () -> {
        if (!hunterAlert) {
            Location l = engine.getOutlaw().getLocation();
            if (isOutside(l)) {
                hunterAlert = true;
                Bukkit.broadcastMessage(Messages.WALL_WALKTHROUGH_ALERT);
            }
        }
    };

    private final Runnable checkEscape = () -> {
        Location l = engine.getOutlaw().getLocation().add(-0.5, 0, -0.5);
        if ((Math.abs(l.getX()) > escapeArea) || (Math.abs(l.getZ()) > escapeArea)) {
            engine.triggerEndgame(true);
        }
    };

    public WallGameModeBase() {
        wallCfg = new WallGameModeConfig();
        Wall.initWith(wallCfg);
        events = new WallEventListener(this);
        Engine engine = Engine.getInstance();
        engine.getServer().getPluginManager().registerEvents(events, engine);
    }

    @Override
    public void onIdle() {
        hunterAlert = false;
        halfSize = wallCfg.playzoneSize / 2 + 1;
        escapeArea = halfSize + wallCfg.wallThickness;
        events.reset();
        WorldBorder wb = Bukkit.getWorlds().get(0).getWorldBorder();
        wb.setCenter(0, 0);
        wb.setSize(escapeArea*2 + 20);
        //generate wall or rollback spots from previous game
        Wall.prepareWall();
    }

    @Override
    public void onPreStart() {
        //generate exit spots
        Wall.prepareSpots();
    }

    @Override
    public void onGame() {
        createWallObjective();
    }

    @Override
    public void tick() {
        alertHunter.run();
        checkEscape.run();
    }

    @Override
    public void unload() {
        Wall.rollback();
        HandlerList.unregisterAll(events);
        LoggerUtil.warn(Messages.UNPLAYABLE_WORLD_WARNING);
    }

    @Override
    public GameModeConfig getConfig() {
        return wallCfg;
    }

    @Override
    public String getObjective() {
        return Messages.WALL_OBJECTIVE;
    }

    @Override
    public String getName() {
        return Messages.WALL_NAME;
    }

    @Override
    public String getDescription() {
        return String.format(Messages.WALL_DESCRIPTION, wallCfg.wallThickness, wallCfg.playzoneSize);
    }

    boolean isOutside(Location l) {
        return ((Math.abs(l.getX()) > halfSize + 1) || (Math.abs(l.getZ()) > halfSize + 1) || l.getY() > 255);
    }

    int getHalfSize() {
        return halfSize;
    }

    private void createWallObjective() {
        Scoreboard sb = engine.getScoreboardHolder().getScoreboard();
        if (sb == null) {
            score = null;
            return;
        }

        Objective objective = engine.getScoreboardHolder().getScoreboard().registerNewObjective("The Wall", "dummy", "The Wall");
        objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        score = objective.getScore(engine.getOutlaw().getName());
        score.setScore(wallCfg.wallThickness);
    }

    public void recalculateScore(Location l) {
        if (score == null)
            return;

        int blocks = Math.max(Math.abs(l.getBlockX()) - halfSize,  Math.abs(l.getBlockZ()) - halfSize) + 1;
        int newScore = wallCfg.wallThickness - blocks;
        if (newScore < score.getScore())
            score.setScore(newScore);
    }
}
