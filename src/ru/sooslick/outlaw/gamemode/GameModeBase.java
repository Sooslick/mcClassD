package ru.sooslick.outlaw.gamemode;

public interface GameModeBase {

    void onIdle();

    void onPreStart();

    void onGame();

    void tick();

    void unload();

    default GameModeConfig getConfig() {
        return null;
    }

    String getObjective();

    String getName();

    String getDescription();
}
