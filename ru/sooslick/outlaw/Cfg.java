package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.lang.reflect.Field;
import java.util.HashMap;

public class Cfg {

    private static final String CANNOT_READ_PARAMETER = "Cannot read parameter %s";
    private static final String READ_STARTINV_ENTRY = "read startInventory entry: %s x %s";
    private static final String UNKNOWN_ITEM = "Unknown item in start inventory: %s";
    private static final String UNKNOWN_PARAMETER = "Unknown parameter: %s";
    private static final String UNPLAYABLE_WORLD_WARNING = "Parameter responsible for modifying world was changed. We strongly recommend to generate a new game world, otherwise it may be unplayable";
    private static final String VALUE_TEMPLATE = "%s: %s";

    private static boolean firstRead = true;
    private static boolean changeAlert = false;
    private static FileConfiguration currentCfg;

    public static boolean debugMode;
    public static int minStartVotes;
    public static int prestartTimer;
    public static int spawnRadius;
    public static int spawnDistance;
    public static int alertRadius;
    public static int alertTimeout;
    public static int hideVictimNametagAbovePlayers;
    public static boolean enablePotionHandicap;
    public static boolean enableStartInventory;
    public static boolean enableEscapeGamemode;
    public static int blocksPerSecondLimit;
    public static int playzoneSize;
    public static int wallThickness;
    public static int spotSize;
    public static int groundSpotQty;
    public static int airSpotQty;
    public static int undergroundSpotQty;
    public static HashMap<Material, Integer> startInventory;

    //disable constructor for utility class
    private Cfg() {}

    public static void readConfig(FileConfiguration f) {
        changeAlert = false;
        currentCfg = f;
                      readValue("debugMode", false);
                      readValue("minStartVotes", 2);
                      readValue("prestartTimer", 60);
                      readValue("spawnRadius", 250);
                      readValue("spawnDistance", 240);
                      readValue("alertRadius", 50);
                      readValue("alertTimeout", 60);
                      readValue("hideVictimNametagAbovePlayers", 4);
                      readValue("enablePotionHandicap", true);
                      readValue("enableStartInventory", true);
        changeAlert = readValue("enableEscapeGamemode", false) || changeAlert;
                      readValue("blocksPerSecondLimit", 100000);
        changeAlert = readValue("playzoneSize", 1000) || changeAlert;
        changeAlert = readValue("wallThickness", 8) || changeAlert;
                      readValue("spotSize", 4);
                      readValue("groundSpotQty", 3);
                      readValue("airSpotQty", 2);
                      readValue("undergroundSpotQty", 5);

        //validate
        if (prestartTimer <= 0) prestartTimer = 10;
        if (spawnRadius <= 0) spawnRadius = 10;
        if (spawnDistance <= 0) spawnDistance = 10;
        if (alertRadius <= 0) alertRadius = 10;
        if (alertTimeout <= 0) alertTimeout = 10;
        if (blocksPerSecondLimit < 10000) blocksPerSecondLimit = 10000;
        if (playzoneSize < spawnRadius + spawnDistance) playzoneSize = spawnRadius + spawnDistance + 10;
        if (wallThickness <= 0) wallThickness = 1;
        if (spotSize <= 0) spotSize = 1;
        if (groundSpotQty < 0) groundSpotQty = 0;
        if (airSpotQty < 0) airSpotQty = 0;
        if (undergroundSpotQty < 0) undergroundSpotQty = 0;
        if (groundSpotQty + airSpotQty + undergroundSpotQty <= 0) groundSpotQty = 1;

        //start inventory
        startInventory = new HashMap<>();
        ConfigurationSection cs = f.getConfigurationSection("startInventory");
        for (String itemName : cs.getKeys(false)) {
            try {
                Material m = Material.valueOf(itemName);
                int qty = cs.getInt(itemName);
                if (qty > 0) {
                    startInventory.put(m, qty);
                    LoggerUtil.debug(String.format(READ_STARTINV_ENTRY, m.name(), qty));
                }
            } catch (IllegalArgumentException e) {
                LoggerUtil.warn(String.format(UNKNOWN_ITEM, itemName));
            }
        }

        //switch log mode
        LoggerUtil.setupLevel();

        //change warnings
        if (changeAlert && !firstRead) {
            LoggerUtil.warn(UNPLAYABLE_WORLD_WARNING);
        }

        //enable change warnings
        firstRead = false;
    }

    //method can return value of any field in this class include non-config variables. Not a bug, f e a t u r e
    public static String getValue(String key) {
        Field f = getField(key);
        if (f == null) {
            return String.format(UNKNOWN_PARAMETER, key);
        }
        try {
            return String.format(VALUE_TEMPLATE, key, f.get(null));
        } catch (Exception e) {
            return String.format(CANNOT_READ_PARAMETER, key);
        }
    }

    //returns true only when field changed
    private static boolean readValue(String key, Object defaultValue) {
        //identify Cfg.class field
        Field f = getField(key);
        if (f == null) {
            LoggerUtil.warn(String.format(UNKNOWN_PARAMETER, key));
            return false;
        }

        //read and set value
        Object oldVal;
        Object newVal;
        boolean modified;
        try {
            oldVal = f.get(null);
            if (f.getType() == Integer.TYPE) {
                int cfgVal = currentCfg.getInt(key, (int) defaultValue);
                modified = cfgVal != (int) oldVal;
                f.set(null, cfgVal);
            } else if (f.getType() == Boolean.TYPE) {
                boolean cfgVal = currentCfg.getBoolean(key, (boolean) defaultValue);
                modified = cfgVal != (boolean) oldVal;
                f.set(null, cfgVal);
            } else {
                //currently this else-branch is unreachable
                throw new Exception();
            }
            newVal = f.get(null);
        } catch (Exception e) {
            LoggerUtil.warn(String.format(CANNOT_READ_PARAMETER, key));
            return false;
        }

        //detect changes
        if (modified) {
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, key, newVal));
        }
        return modified;
    }

    private static Field getField(String key) {
        try {
            return Cfg.class.getField(key);
        } catch (Exception e) {
            return null;
        }
    }
}
