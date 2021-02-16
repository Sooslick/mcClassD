package ru.sooslick.outlaw.gamemode.evacuation;

import com.google.common.collect.ImmutableList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeConfig;

import java.util.Arrays;
import java.util.List;

public class EvacuationConfig implements GameModeConfig {
    private static final ImmutableList<String> AVAILABLE_COMMANDS;

    private boolean firstRead = true;

    int playzoneSize;
    int waitTime;
    int landingTime;
    int cordonTime;
    int cordonZoneSize;
    boolean endGameAfterCordon;

    static {
        AVAILABLE_COMMANDS = ImmutableList.copyOf(Arrays.asList("playzoneSize", "waitTime", "landingTime",
                "cordonTime", "cordonZoneSize", "endGameAfterCordon"));
    }

    @Override
    public void readConfig() {
        FileConfiguration cfg = Engine.getInstance().getConfig();

        //read
        playzoneSize = readAndDetectChanges(cfg, "playzoneSize", 1000, playzoneSize);
        waitTime = readAndDetectChanges(cfg, "waitTime", 1500, waitTime);
        landingTime = readAndDetectChanges(cfg, "landingTime", 300, landingTime);
        cordonTime = readAndDetectChanges(cfg, "cordonTime", 300, cordonTime);
        cordonZoneSize = readAndDetectChanges(cfg, "cordonZoneSize", 16, cordonZoneSize);
        endGameAfterCordon = readAndDetectChanges(cfg, "endGameAfterCordon", true, endGameAfterCordon);

        //validate
        int spawnArea = (Cfg.spawnRadius + Cfg.spawnDistance) * 2 + 10;
        if (playzoneSize < spawnArea) playzoneSize = spawnArea;
        if (waitTime <= 0) waitTime = 10;
        if (landingTime <= 0) landingTime = 10;
        if (cordonTime <= 0) cordonTime = 10;
        if (cordonZoneSize <= 10) cordonZoneSize = 10;

        firstRead = false;
    }

    @Override
    public String getValueOf(String field) {
        switch (field.toLowerCase()) {
            case "playzonesize": return String.valueOf(playzoneSize);
            case "waittime": return String.valueOf(waitTime);
            case "landingtime": return String.valueOf(landingTime);
            case "cordontime": return String.valueOf(cordonTime);
            case "cordonzonesize": return String.valueOf(cordonZoneSize);
            case "endgameaftercordon": return String.valueOf(endGameAfterCordon);
            default: return null;
        }
    }

    @Override
    public List<String> availableParameters() {
        return AVAILABLE_COMMANDS;
    }

    //weird copypaste from Wall
    private int readAndDetectChanges(FileConfiguration cfg, String param, int def, int oldVal) {
        int newVal = cfg.getInt(param, def);
        if (!firstRead && oldVal != newVal) {
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, param, newVal));
        }
        return newVal;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean readAndDetectChanges(FileConfiguration cfg, String param, boolean def, boolean oldVal) {
        boolean newVal = cfg.getBoolean(param, def);
        if (!firstRead && oldVal != newVal) {
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, param, newVal));
        }
        return newVal;
    }
}
