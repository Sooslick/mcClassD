package ru.sooslick.outlaw.gamemode;

import java.util.List;

public interface GameModeConfig {

    /**
     * Read the gamemode's config
     */
    void readConfig();

    /**
     * Return the value of specified config's parameter
     * @param field the specified parameter
     * @return String value of this field or null if field is not present in the config
     */
    default String getValueOf(String field) {
        return null;
    }

    /**
     * Return the available parameters list
     * @return available parameters list
     */
    List<String> availableParameters();
}
