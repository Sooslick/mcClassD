package ru.sooslick.outlaw.gamemode;

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
}
