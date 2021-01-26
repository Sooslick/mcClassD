package ru.sooslick.outlaw.gamemode;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * The main gamemode interface
 */
public interface GameModeBase {

    /**
     * Switch the game to idle state and init gamemode
     */
    void onIdle();

    /**
     * Switch the game to pre-launch state and late-init gamemode
     */
    void onPreStart();

    /**
     * Launch game
     */
    void onGame();

    /**
     * Game event that are triggered every second
     */
    void tick();

    /**
     * Unload gamemode and rollback changes after switching to another gamemode
     */
    void unload();

    /**
     * Return the config of this gamemode
     * @return instance of gamemode's config
     */
    default GameModeConfig getConfig() {
        return null;
    }

    /**
     * Return the Victim's objective of this gamemode
     * @return simple objective description
     */
    String getObjective();

    /**
     * Return the gamemode's name
     * @return the name of this gamemode
     */
    String getName();

    /**
     * Return the detailed description of gamemode
     * @return detailed description of this gamemode
     */
    String getDescription();

    /**
     * Test if the gamemode selects spawns for players
     * @return true if the gamemode selects spawns for players
     */
    default boolean customSpawnEnabled() {
        return false;
    }

    /**
     * Return the location where the Victim will spawn
     * @return location where the Victim will spawn
     */
    default @NotNull Location getVictimSpawn() {
        return new Location(null, 0, 0, 0);
    }

    /**
     * Return the location where Hunters will spawn
     * @return location where Hunters will spawn
     */
    default @NotNull Location getHunterSpawn() {
        return new Location(null, 0, 0, 0);
    }
}
