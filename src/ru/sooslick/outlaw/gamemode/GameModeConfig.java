package ru.sooslick.outlaw.gamemode;

import java.util.List;

public interface GameModeConfig {

    /**
     * Read the gamemode's config
     */
    void readConfig();

    String getValueOf(String field);

    String availableParameters();
}
