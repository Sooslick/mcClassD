package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import ru.sooslick.outlaw.util.LoggerUtil;

public class ScoreboardHolder {
    public static final String TEAM_HUNTER_NAME = "Hunter";
    public static final String TEAM_VICTIM_NAME = "Victim";

    private static final String DEBUG_HOLDER_CREATED = "Registered new ScoreboardHolder, enabled = ";
    private static final String DEBUG_SET_SCOREBOARD = "Set custom scoreboard for player ";

    private Scoreboard scoreboard;
    private Team teamVictim, teamHunter;
    private boolean enabled;

    public ScoreboardHolder(ScoreboardManager sbm) {
        if (sbm != null) {
            enabled = true;
            scoreboard = sbm.getNewScoreboard();
            teamVictim = scoreboard.registerNewTeam(TEAM_VICTIM_NAME);
            teamHunter = scoreboard.registerNewTeam(TEAM_HUNTER_NAME);

            //pre-setup
            teamVictim.setColor(ChatColor.GOLD);
            teamHunter.setColor(ChatColor.WHITE);
        } else
            enabled = false;
        LoggerUtil.debug(DEBUG_HOLDER_CREATED + enabled);
    }

    // for rejoin bugfix
    public void setPlayerScoreboard(Player p) {
        if (scoreboard != null)
            p.setScoreboard(scoreboard);
        LoggerUtil.debug(DEBUG_SET_SCOREBOARD + p.getName());
    }

    public void addVictim(Player p) {
        addPlayerToTeam(p, teamVictim);

        //todo: wall
        victim = p.getName();
    }

    public void addHunter(Player p) {
        addPlayerToTeam(p, teamHunter);
    }

    public void recalculateNametagVisiblity(int hunters) {
        if (enabled) {
            boolean invisible = hunters > Cfg.hideVictimNametagAboveHunters;
            teamVictim.setOption(Team.Option.NAME_TAG_VISIBILITY, invisible ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
            Bukkit.broadcastMessage(invisible ? Messages.NAMETAG_IS_INVISIBLE : Messages.NAMETAG_IS_VISIBLE);
            return;
        }
        // if sb is disabled, nametag is always visible
        Bukkit.broadcastMessage(Messages.NAMETAG_IS_VISIBLE);
    }

    private void addPlayerToTeam(Player p, Team t) {
        if (enabled) {
            setPlayerScoreboard(p);
            t.addEntry(p.getName());
        }
    }

    /////////////////////////////////////////////////////////////////////////
    //todo move entire section to wall

    private Score score;
    private String victim;

    public void createWallObjective() {
        Objective objective = scoreboard.registerNewObjective("The Wall", "dummy", "The Wall");
        objective.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        score = objective.getScore(victim);
        score.setScore(Cfg.wallThickness);
    }

    public void recalculateScore(Location l) {
        int halfsize = Engine.getInstance().getHalfSize();
        int blocks = Math.max(Math.abs(l.getBlockX()) - halfsize,  Math.abs(l.getBlockZ()) - halfsize) + 1;
        int newScore = Cfg.wallThickness - blocks;
        if (newScore < score.getScore())
            score.setScore(newScore);
    }
}
