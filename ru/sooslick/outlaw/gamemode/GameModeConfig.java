package ru.sooslick.outlaw.gamemode;

public interface GameModeConfig {

    void readConfig();

    String getValueOf(String field);

    String availableParameters();
}
