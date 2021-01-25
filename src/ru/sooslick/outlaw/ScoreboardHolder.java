package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import ru.sooslick.outlaw.util.LoggerUtil;

/**
 * Represents the game's scoreboard which will function in the current game
 */
public class ScoreboardHolder {
    public static final String TEAM_HUNTER_NAME = "Hunter";
    public static final String TEAM_VICTIM_NAME = "Victim";

    private static final String DEBUG_HOLDER_CREATED = "Registered new ScoreboardHolder, enabled = ";
    private static final String DEBUG_SET_SCOREBOARD = "Set custom scoreboard for player ";

    private Scoreboard scoreboard;
    private Team teamVictim, teamHunter;
    private final boolean enabled;

    ScoreboardHolder(ScoreboardManager sbm) {
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

    /**
     * Return the current Scoreboard
     * @return current Scoreboard
     */
    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    /**
     * Fix custom scoreboard so the player can see current objectives and teams
     * @param p player requires the fix
     */
    // for rejoin bugfix
    public void setPlayerScoreboard(Player p) {
        if (scoreboard != null)
            p.setScoreboard(scoreboard);
        LoggerUtil.debug(DEBUG_SET_SCOREBOARD + p.getName());
    }

    void addVictim(Player p) {
        addPlayerToTeam(p, teamVictim);
    }

    void addHunter(Player p) {
        addPlayerToTeam(p, teamHunter);
    }

    void recalculateNametagVisiblity(int hunters) {
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
}
