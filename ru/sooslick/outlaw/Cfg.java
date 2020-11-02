package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.logging.Logger;

public class Cfg {

    public static boolean firstRead = true;
    public static boolean changeAlert = false;
    public static FileConfiguration currentCfg;

    public static boolean debugMode;
    public static int minVotestarters;
    public static int votestartTimer;
    public static int spawnRadius;
    public static int spawnDistance;
    public static int alertRadius;
    public static int alertTimeout;
    public static int hideNametagFrom;
    public static boolean enablePotionHandicap;
    public static boolean enableStartInventory;
    public static boolean enableEscapeGamemode;
    public static int blocksPerSecondLimit;
    public static int playzoneSize;
    public static int wallThickness;
    public static int spotSize;
    public static int groundSpotDensity;
    public static int airSpotDensity;
    public static int undergroundSpotDensity;
    public static HashMap<Material, Integer> startInventory;

    private static final String SET = "§cGame parameter changed: §e";
    private static final Logger LOG = Bukkit.getLogger();

    public static void readConfig(FileConfiguration f) {
        changeAlert = false;
        currentCfg = f;
                      readValue("debugMode", false);
                      readValue("minVotestarters", 2);
                      readValue("votestartTimer", 60);
                      readValue("spawnRadius", 250);
                      readValue("spawnDistance", 240);
                      readValue("alertRadius", 50);
                      readValue("alertTimeout", 60);
                      readValue("hideNametagFrom", 4);
                      readValue("enablePotionHandicap", true);
                      readValue("enableStartInventory", true);
        changeAlert = readValue("enableEscapeGamemode", false);
                      readValue("blocksPerSecondLimit", 100000);
        changeAlert = readValue("playzoneSize", 1000);
        changeAlert = readValue("wallThickness", 8);
                      readValue("spotSize", 4);
                      readValue("groundSpotDensity", 3);
                      readValue("airSpotDensity", 2);
                      readValue("undergroundSpotDensity", 5);

        if (changeAlert && !firstRead) {
            LOG.warning("Parameter responsible for world modifying is changed. We strongly recommend to recreate game world, otherwise it may be unplayable");
        }

        //start inventory
        startInventory = new HashMap<>();
        ConfigurationSection cs = f.getConfigurationSection("startInventory");
        for (String k : cs.getKeys(false)) {
            try {
                Material m = Material.valueOf(k);
                int i = cs.getInt(k);
                startInventory.put(m, i);
                if (debugMode)
                    LOG.info("startInventory.put: " + m.name() + " x " + i);
            } catch (IllegalArgumentException e) {
                LOG.warning("Unknown item in start inventory: " + k);
            }
        }

        firstRead = false;
    }

    //returns true only when field changed
    private static boolean readValue(String key, Object defaultValue) {
        //identify Cfg.class field
        Field f;
        try {
            f = Cfg.class.getField(key);
        } catch (Exception e) {
            LOG.warning("Unknown cfg field " + key + "\n" + e.getMessage());
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
            LOG.warning("Cannot read value of field " + key + "\n" + e.getMessage());
            return false;
        }

        //detect changes
        if (oldVal != newVal) {
            Bukkit.broadcastMessage(SET + key + " = " + newVal);
            return true;
        }
        return false;
    }
}
