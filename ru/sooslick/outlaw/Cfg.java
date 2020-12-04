package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.lang.reflect.Field;
import java.util.HashMap;

public class Cfg {

    public static boolean firstRead = true;
    public static boolean changeAlert = false;
    public static FileConfiguration currentCfg;

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

    private static final String SET = "§cGame parameter changed: §e";

    //disable constructor for utility class
    private Cfg() {}

    public static void readConfig(FileConfiguration f) {
        changeAlert = false;    //todo change alert bug
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
        changeAlert = readValue("enableEscapeGamemode", false);     //todo bigfix
                      readValue("blocksPerSecondLimit", 100000);
        changeAlert = readValue("playzoneSize", 1000);              //todo and here too
        changeAlert = readValue("wallThickness", 8);
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
        for (String k : cs.getKeys(false)) {
            try {
                Material m = Material.valueOf(k);
                int i = cs.getInt(k);
                startInventory.put(m, i);
                LoggerUtil.debug("startInventory.put: " + m.name() + " x " + i);
            } catch (IllegalArgumentException e) {
                LoggerUtil.warn("Unknown item in start inventory: " + k);
            }
        }

        //switch log mode
        LoggerUtil.setupLevel();

        //change warnings
        if (changeAlert && !firstRead) {
            LoggerUtil.warn("Parameter responsible for modifying world was changed. " +
                    "We strongly recommend to generate a new game world, " +
                    "otherwise it may be unplayable");
        }

        //enable change warnings
        firstRead = false;
    }

    //method can return value of any field in this class include non-config variables. Not a bug, f e a t u r e
    public static String getValue(String key) {
        Field f = getField(key);
        if (f == null) {
            return "Unknown parameter: " + key;
        }
        try {
            return key + ": " + f.get(null);
        } catch (Exception e) {
            return "Cannot read parameter: " + key;
        }
    }

    //returns true only when field changed
    private static boolean readValue(String key, Object defaultValue) {
        //identify Cfg.class field
        Field f = getField(key);
        if (f == null) {
            LoggerUtil.warn("Unknown cfg field " + key);
            return false;
        }

        //read and set value
        Object oldVal = null;
        Object newVal = null;
        try {
            oldVal = f.get(null);
            if (f.getType() == Integer.TYPE) {
                f.set(null, currentCfg.getInt(key, (int) defaultValue));
            } else if (f.getType() == Boolean.TYPE) {
                f.set(null, currentCfg.getBoolean(key, (boolean) defaultValue));
            }
            newVal = f.get(null);
        } catch (Exception e) {
            LoggerUtil.warn("Cannot read value of field " + key + "\n" + e.getMessage());
            return false;
        }

        //detect changes
        if (!oldVal.toString().equals(newVal.toString())) {         //WEIRD. FIXME PLZ?
            Bukkit.broadcastMessage(SET + key + " = " + newVal);
            return true;
        }
        return false;
    }

    private static Field getField(String key) {
        try {
            return Cfg.class.getField(key);
        } catch (Exception e) {
            return null;
        }
    }
}
