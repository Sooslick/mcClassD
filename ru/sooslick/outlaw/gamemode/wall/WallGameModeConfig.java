package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.util.LoggerUtil;

public class WallGameModeConfig implements GameModeConfig {
    static final String UNPLAYABLE_WORLD_WARNING = "Parameter responsible for modifying world was changed. We strongly recommend to generate a new game world, otherwise it may be unplayable";

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
        playzoneSize = cfg.getInt("playzoneSize", 1000);
        wallThickness = cfg.getInt("wallThickness", 8);
        spotSize = cfg.getInt("spotSize", 4);
        groundSpotQty = cfg.getInt("groundSpotQty", 3);
        airSpotQty = cfg.getInt("airSpotQty", 2);
        undergroundSpotQty = cfg.getInt("undergroundSpotQty", 5);

        //validate
        if (playzoneSize < Cfg.spawnRadius + Cfg.spawnDistance) playzoneSize = Cfg.spawnRadius + Cfg.spawnDistance + 10;
        if (wallThickness <= 0) wallThickness = 1;
        if (spotSize <= 0) spotSize = 1;
        if (groundSpotQty < 0) groundSpotQty = 0;
        if (airSpotQty < 0) airSpotQty = 0;
        if (undergroundSpotQty < 0) undergroundSpotQty = 0;
        if (groundSpotQty + airSpotQty + undergroundSpotQty <= 0) groundSpotQty = 1;

        if ((oldZone != playzoneSize || oldThin != wallThickness) && !firstRead)
            LoggerUtil.warn(UNPLAYABLE_WORLD_WARNING);

        firstRead = false;

        //todo: changes broadcast
    }

    @Override
    public String availableParameters() {
        return "playzoneSize, wallThickness, spotSize, groundSpotQty, airSpotQty, undergroundSpotQty";
    }
}
