package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.gamemode.anypercent.AnyPercentBase;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class Cfg {

    private static final String CANNOT_LOAD_GAMEMODE = "§4Cannot load gamemode class %s";
    private static final String CANNOT_READ_PARAMETER = "Cannot read parameter %s";
    private static final String INVALID_CLASS_EXCEPTION = " is not GameModeBase class";
    private static final String READ_STARTINV_ENTRY = "read startInventory entry: %s x %s";
    private static final String UNKNOWN_ITEM = "Unknown item in start inventory: %s";
    private static final String UNKNOWN_METHOD = "Unknown compass update method: %s";
    private static final String UNKNOWN_PARAMETER = "Unknown parameter: %s";
    private static final String VALUE_TEMPLATE = "%s: %s";

    private static FileConfiguration currentCfg;
    private static GameModeConfig gameModeCfg;

    public static boolean debugMode;
    public static int blocksPerSecondLimit;
    public static Map<String, String> gamemodes;
    public static Class<? extends GameModeBase> preferredGamemode;
    public static int minStartVotes;
    public static int prestartTimer;
    public static int spawnRadius;
    public static int spawnDistance;
    public static int hideVictimNametagAboveHunters;
    public static boolean enablePotionHandicap;
    public static boolean enableStartInventory;
    public static int alertRadius;
    public static int alertTimeout;
    public static CompassUpdates compassUpdates;
    public static int compassUpdatesPeriod;
    public static boolean enableVictimGlowing;
    public static int milkGlowImmunityDuration;
    public static HashMap<Material, Integer> startInventory;

    //disable constructor for utility class
    private Cfg() {}

    public static void readConfig(FileConfiguration f) {

        currentCfg = f;
        readValue("debugMode", false);
        readValue("blocksPerSecondLimit", 100000);
        readValue("minStartVotes", 2);
        readValue("prestartTimer", 60);
        readValue("spawnRadius", 250);
        readValue("spawnDistance", 240);
        readValue("hideVictimNametagAboveHunters", 2);
        readValue("enablePotionHandicap", true);
        readValue("enableStartInventory", true);
        readValue("alertRadius", 50);
        readValue("alertTimeout", 60);
        readValue("compassUpdatesPeriod", 1);
        readValue("enableVictimGlowing", false);
        readValue("milkGlowImmunityDuration", 180);

        //validate
        if (prestartTimer <= 0) prestartTimer = 10;
        if (spawnRadius <= 0) spawnRadius = 10;
        if (spawnDistance <= 0) spawnDistance = 10;
        if (alertRadius <= 0) alertRadius = 10;
        if (alertTimeout <= 0) alertTimeout = 10;
        if (compassUpdatesPeriod <= 0) compassUpdatesPeriod = 1;
        if (milkGlowImmunityDuration <= 0) milkGlowImmunityDuration = 10;
        if (blocksPerSecondLimit < 10000) blocksPerSecondLimit = 10000;

        //something special for CompassUpdates
        String key = "compassUpdates";
        String compassUpdateCfg = currentCfg.getString(key, "ALWAYS");
        CompassUpdates old = compassUpdates;
        try {
            //noinspection ConstantConditions
            compassUpdates = CompassUpdates.valueOf(compassUpdateCfg.toUpperCase());
        } catch (IllegalArgumentException e) {
            LoggerUtil.warn(String.format(UNKNOWN_METHOD, compassUpdateCfg));
            compassUpdates = CompassUpdates.ALWAYS;
        }
        if (old != compassUpdates)
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, key, compassUpdates));

        //something special for start inventory
        startInventory = new HashMap<>();
        ConfigurationSection cs = f.getConfigurationSection("startInventory");
        if (cs != null) {
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
        }

        //something special for gamemodes
        gamemodes = new HashMap<>();
        cs = f.getConfigurationSection("gamemodes");
        if (cs != null)
            for (String gmName : cs.getKeys(false))
                gamemodes.put(gmName, cs.getString(gmName));
        String className = gamemodes.get(f.getString("preferredGamemode"));
        try {
            Class<?> clazz = Class.forName(className);
            if (!GameModeBase.class.isAssignableFrom(clazz))
                throw new Exception(clazz.getName() + INVALID_CLASS_EXCEPTION);
            preferredGamemode = clazz.asSubclass(GameModeBase.class);
        } catch (Exception e) {
            preferredGamemode = AnyPercentBase.class;
            LoggerUtil.warn(e.getMessage());
            LoggerUtil.warn(String.format(CANNOT_LOAD_GAMEMODE, className));
        }

        //switch log mode
        LoggerUtil.setupLevel();
    }

    public static void readGameModeConfig(GameModeBase gmb) {
        gameModeCfg = gmb.getConfig();
        if (gameModeCfg != null)
            gameModeCfg.readConfig();
    }

    public static String availableParameters() {
        StringBuilder sb = new StringBuilder().append(Messages.AVAILABLE_PARAMETERS).append("debugMode, blocksPerSecondLimit, gamemodes, preferredGamemode, minStartVotes, prestartTimer, spawnRadius, spawnDistance, hideVictimNametagAboveHunters, enablePotionHandicap, enableStartInventory, alertRadius, alertTimeout, compassUpdates, compassUpdatesPeriod, enableVictimGlowing, milkGlowImmunityDuration, startInventory");
        if (gameModeCfg == null)
            return sb.toString();
        String gmParams = gameModeCfg.availableParameters();
        if (gmParams.length() == 0)
            return sb.toString();
        return sb.append(", ").append(gmParams).toString();
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
    private static void readValue(String key, Object defaultValue) {
        //identify Cfg.class field
        Field f = getField(key);
        if (f == null) {
            LoggerUtil.warn(String.format(UNKNOWN_PARAMETER, key));
            return;
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
            return;
        }

        //detect changes
        if (modified) {
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, key, newVal));
        }
    }

    private static Field getField(String key) {
        try {
            return Cfg.class.getField(key);
        } catch (Exception e) {
            return null;
        }
    }
}
