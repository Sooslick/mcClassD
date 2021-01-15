package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.util.LoggerUtil;

public class WallGameModeConfig implements GameModeConfig {

    private boolean firstRead = true;

    public int playzoneSize;
    public int wallThickness;
    public int spotSize;
    public int groundSpotQty;
    public int airSpotQty;
    public int undergroundSpotQty;

    @Override
    public void readConfig() {
        FileConfiguration cfg = Engine.getInstance().getConfig();

        int oldZone = playzoneSize;
        int oldThin = wallThickness;

        //read
        playzoneSize = readAndDetectChanges(cfg, "playzoneSize", 1000, playzoneSize);
        wallThickness = readAndDetectChanges(cfg, "wallThickness", 8, wallThickness);
        spotSize = readAndDetectChanges(cfg, "spotSize", 4, spotSize);
        groundSpotQty = readAndDetectChanges(cfg, "groundSpotQty", 3, groundSpotQty);
        airSpotQty = readAndDetectChanges(cfg, "airSpotQty", 2, airSpotQty);
        undergroundSpotQty = readAndDetectChanges(cfg, "undergroundSpotQty", 5, undergroundSpotQty);

        //validate
        if (playzoneSize < Cfg.spawnRadius + Cfg.spawnDistance) playzoneSize = Cfg.spawnRadius + Cfg.spawnDistance + 10;
        if (wallThickness <= 0) wallThickness = 1;
        if (spotSize <= 0) spotSize = 1;
        else if (spotSize > 30) spotSize = 30;
        if (groundSpotQty < 0) groundSpotQty = 0;
        if (airSpotQty < 0) airSpotQty = 0;
        if (undergroundSpotQty < 0) undergroundSpotQty = 0;
        if (groundSpotQty + airSpotQty + undergroundSpotQty <= 0) groundSpotQty = 1;

        if ((oldZone != playzoneSize || oldThin != wallThickness) && !firstRead)
            LoggerUtil.warn(Messages.UNPLAYABLE_WORLD_WARNING);

        firstRead = false;
    }

    @Override
    public String availableParameters() {
        return "playzoneSize, wallThickness, spotSize, groundSpotQty, airSpotQty, undergroundSpotQty";
    }

    //weird method
    private int readAndDetectChanges(FileConfiguration cfg, String param, int def, int oldVal) {
        int newVal = cfg.getInt(param, def);
        if (!firstRead && oldVal != newVal) {
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, param, newVal));
        }
        return newVal;
    }
}
