package ru.sooslick.outlaw;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.GameModeConfig;
import ru.sooslick.outlaw.gamemode.anypercent.AnyPercentBase;
import ru.sooslick.outlaw.util.LoggerUtil;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Represents the main Manhunt config
 */
public class Cfg {
    private static final String DEBUG_MODE = "debugMode";
    private static final String BPS_LIMIT = "blocksPerSecondLimit";
    private static final String GAMEMODES = "gamemodes";
    private static final String PREFERRED_GAMEMODE = "preferredGamemode";
    private static final String MIN_START_VOTES = "minStartVotes";
    private static final String PRESTART_TIMER = "prestartTimer";
    private static final String PRINT_ENDGAME_STATS = "printEndgameStatistics";
    private static final String SPAWN_RADIUS = "spawnRadius";
    private static final String SPAWN_DISTANCE = "spawnDistance";
    private static final String HIDE_ABOVE = "hideVictimNametagAboveHunters";
    private static final String EN_POTION_HANDICAP = "enablePotionHandicap";
    private static final String EN_START_INVENTORY = "enableStartInventory";
    private static final String ALERT_RADIUS = "alertRadius";
    private static final String ALERT_TIMEOUT = "alertTimeout";
    private static final String EN_FRIENDLY_FIRE = "friendlyFireEnabled";
    private static final String DENY_NETHER_TRAVELLING = "denyNetherTravelling";
    private static final String COMPASS_UPDATES = "compassUpdates";
    private static final String COMPASS_UPDATES_PERIOD = "compassUpdatesPeriod";
    private static final String EN_VICTIM_GLOWING = "enableVictimGlowing";
    private static final String IMMUNITY_DURATION = "milkGlowImmunityDuration";
    private static final String VICTIM_START_INVENTORY = "victimStartInventory";
    private static final String HUNTER_START_INVENTORY = "hunterStartInventory";

    private static final String CANNOT_LOAD_GAMEMODE = "§4Cannot load gamemode class %s";
    private static final String INVALID_CLASS_EXCEPTION = " is not GameModeBase class";
    private static final String READ_STARTINV_ENTRY = "read startInventory entry: %s x %s";
    private static final String UNKNOWN_ITEM = "Unknown item in start inventory: %s";
    private static final String UNKNOWN_METHOD = "Unknown compass update method: %s";
    private static final String UNKNOWN_PARAMETER = "Unknown parameter: %s";
    private static final String VALUE_TEMPLATE = "%s: %s";

    private static final ImmutableList<String> PARAMETERS;
    private static final ImmutableMap<String, Object> defaultValues;

    private static FileConfiguration currentCfg;
    private static GameModeConfig gameModeCfg;
    private static Map<String, Object> tempValues;

    public static boolean debugMode;
    public static int blocksPerSecondLimit;
    public static Map<String, String> gamemodes;
    public static Class<? extends GameModeBase> preferredGamemode;
    public static int minStartVotes;
    public static int prestartTimer;
    public static boolean printEndgameStatistics;
    public static int spawnRadius;
    public static int spawnDistance;
    public static int hideVictimNametagAboveHunters;
    public static boolean enablePotionHandicap;
    public static boolean enableStartInventory;
    public static int alertRadius;
    public static int alertTimeout;
    public static boolean friendlyFireEnabled;
    public static boolean denyNetherTravelling;
    public static CompassUpdates compassUpdates;
    public static int compassUpdatesPeriod;
    public static boolean enableVictimGlowing;
    public static int milkGlowImmunityDuration;
    public static HashMap<Material, Integer> victimStartInventory;
    public static HashMap<Material, Integer> hunterStartInventory;

    static {
        PARAMETERS = ImmutableList.copyOf(Arrays.asList(DEBUG_MODE, BPS_LIMIT, GAMEMODES,
                PREFERRED_GAMEMODE, MIN_START_VOTES, PRESTART_TIMER, PRINT_ENDGAME_STATS, SPAWN_RADIUS, SPAWN_DISTANCE,
                HIDE_ABOVE, EN_POTION_HANDICAP, EN_START_INVENTORY,
                ALERT_RADIUS, ALERT_TIMEOUT, EN_FRIENDLY_FIRE, DENY_NETHER_TRAVELLING,
                COMPASS_UPDATES, COMPASS_UPDATES_PERIOD, EN_VICTIM_GLOWING, IMMUNITY_DURATION, VICTIM_START_INVENTORY, HUNTER_START_INVENTORY));

        defaultValues = ImmutableMap.copyOf(new HashMap<String, Object>() {{
            put(DEBUG_MODE, false);
            put(BPS_LIMIT, 100000);
            put(MIN_START_VOTES, 2);
            put(PRESTART_TIMER, 60);
            put(PRINT_ENDGAME_STATS, true);
            put(SPAWN_RADIUS, 250);
            put(SPAWN_DISTANCE, 240);
            put(HIDE_ABOVE, 2);
            put(EN_POTION_HANDICAP, true);
            put(EN_START_INVENTORY, true);
            put(ALERT_RADIUS, 50);
            put(ALERT_TIMEOUT, 60);
            put(EN_FRIENDLY_FIRE, true);
            put(DENY_NETHER_TRAVELLING, true);
            put(COMPASS_UPDATES_PERIOD, 1);
            put(EN_VICTIM_GLOWING, false);
            put(IMMUNITY_DURATION, 180);
        }});
    }

    //disable constructor for utility class
    private Cfg() {}

    /**
     * Read Manhunt's config
     * @param f Manhunt's configuration file
     */
    public static void readConfig(FileConfiguration f) {
        tempValues = new HashMap<>();
        currentCfg = f;
        readValue(DEBUG_MODE);
        readValue(BPS_LIMIT);
        readValue(MIN_START_VOTES);
        readValue(PRESTART_TIMER);
        readValue(PRINT_ENDGAME_STATS);
        readValue(SPAWN_RADIUS);
        readValue(SPAWN_DISTANCE);
        readValue(HIDE_ABOVE);
        readValue(EN_POTION_HANDICAP);
        readValue(EN_START_INVENTORY);
        readValue(ALERT_RADIUS);
        readValue(ALERT_TIMEOUT);
        readValue(EN_FRIENDLY_FIRE);
        readValue(DENY_NETHER_TRAVELLING);
        readValue(COMPASS_UPDATES_PERIOD);
        readValue(EN_VICTIM_GLOWING);
        readValue(IMMUNITY_DURATION);

        //validate
        debugMode = validateBool(debugMode, DEBUG_MODE);
        blocksPerSecondLimit = validateInt(blocksPerSecondLimit, BPS_LIMIT, i -> i >= 10000);
        minStartVotes = validateInt(minStartVotes, MIN_START_VOTES, i -> true);
        prestartTimer = validateInt(prestartTimer, PRESTART_TIMER, i -> i > 0);
        printEndgameStatistics = validateBool(printEndgameStatistics, PRINT_ENDGAME_STATS);
        spawnRadius = validateInt(spawnRadius, SPAWN_RADIUS, i -> i > 0);
        spawnDistance = validateInt(spawnDistance, SPAWN_DISTANCE, i -> i > 0);
        hideVictimNametagAboveHunters = validateInt(hideVictimNametagAboveHunters, HIDE_ABOVE, i -> true);
        enablePotionHandicap = validateBool(enablePotionHandicap, EN_POTION_HANDICAP);
        enableStartInventory = validateBool(enableStartInventory, EN_START_INVENTORY);
        alertRadius = validateInt(alertRadius, ALERT_RADIUS, i -> i > 0);
        alertTimeout = validateInt(alertTimeout, ALERT_TIMEOUT, i -> i > 0);
        friendlyFireEnabled = validateBool(friendlyFireEnabled, EN_FRIENDLY_FIRE);
        denyNetherTravelling = validateBool(denyNetherTravelling, DENY_NETHER_TRAVELLING);
        compassUpdatesPeriod = validateInt(compassUpdatesPeriod, COMPASS_UPDATES_PERIOD, i -> i > 0);
        enableVictimGlowing = validateBool(enableVictimGlowing, EN_VICTIM_GLOWING);
        milkGlowImmunityDuration = validateInt(milkGlowImmunityDuration, IMMUNITY_DURATION, i -> i > 0);

        //something special for CompassUpdates
        String compassUpdateCfg = currentCfg.getString(COMPASS_UPDATES, "ALWAYS");
        CompassUpdates old = compassUpdates;
        try {
            compassUpdates = CompassUpdates.valueOf(compassUpdateCfg.toUpperCase());
        } catch (IllegalArgumentException e) {
            LoggerUtil.warn(String.format(UNKNOWN_METHOD, compassUpdateCfg));
            compassUpdates = CompassUpdates.ALWAYS;
        }
        if (old != compassUpdates)
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, COMPASS_UPDATES, compassUpdates));

        //something special for start inventory
        victimStartInventory = fillInventoryMap(f.getConfigurationSection(VICTIM_START_INVENTORY));
        hunterStartInventory = fillInventoryMap(f.getConfigurationSection(HUNTER_START_INVENTORY));

        //something special for gamemodes
        gamemodes = new HashMap<>();
        ConfigurationSection cs = f.getConfigurationSection(GAMEMODES);
        if (cs != null)
            for (String gmName : cs.getKeys(false))
                gamemodes.put(gmName, cs.getString(gmName));
        String className = gamemodes.get(f.getString(PREFERRED_GAMEMODE));
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

    static void readGameModeConfig(GameModeBase gmb) {
        gameModeCfg = gmb.getConfig();
        if (gameModeCfg != null)
            gameModeCfg.readConfig();
    }

    /**
     * Return the list of parameters that are available in main and gamemode's config
     * @return list of parameters
     */
    public static List<String> availableParameters() {
        if (gameModeCfg == null)
            return new LinkedList<>(PARAMETERS);
        else {
            List<String> result = new LinkedList<>(PARAMETERS);
            result.addAll(gameModeCfg.availableParameters());
            return result;
        }
    }

    /**
     * Format string of parameters that are available in main and gamemode's config
     * @return formatted string
     */
    public static String formatAvailableParameters() {
        return Messages.AVAILABLE_PARAMETERS + String.join(", ", availableParameters());
    }

    /**
     * Return string value of config's parameter
     * @param key parameter
     * @return String value
     */
    //method can return value of any field in this class include non-config variables. Not a bug, but kinda sus
    public static String getValue(String key) {
        if (!PARAMETERS.contains(key)) {
            String str = gameModeCfg == null ? null : gameModeCfg.getValueOf(key);
            return str == null ? String.format(UNKNOWN_PARAMETER, key) : String.format(VALUE_TEMPLATE, key, str);
        } else {
            switch (key) {
                case DEBUG_MODE: return String.format(VALUE_TEMPLATE, key, debugMode);
                case BPS_LIMIT: return String.format(VALUE_TEMPLATE, key, blocksPerSecondLimit);
                case GAMEMODES: return String.format(VALUE_TEMPLATE, key, gamemodes);
                case PREFERRED_GAMEMODE: return String.format(VALUE_TEMPLATE, key, Engine.getInstance().getGameMode().getName());
                case MIN_START_VOTES: return String.format(VALUE_TEMPLATE, key, minStartVotes);
                case PRESTART_TIMER: return String.format(VALUE_TEMPLATE, key, prestartTimer);
                case PRINT_ENDGAME_STATS: return String.format(VALUE_TEMPLATE, key, printEndgameStatistics);
                case SPAWN_RADIUS: return String.format(VALUE_TEMPLATE, key, spawnRadius);
                case SPAWN_DISTANCE: return String.format(VALUE_TEMPLATE, key, spawnDistance);
                case HIDE_ABOVE: return String.format(VALUE_TEMPLATE, key, hideVictimNametagAboveHunters);
                case EN_POTION_HANDICAP: return String.format(VALUE_TEMPLATE, key, enablePotionHandicap);
                case EN_START_INVENTORY: return String.format(VALUE_TEMPLATE, key, enableStartInventory);
                case ALERT_RADIUS: return String.format(VALUE_TEMPLATE, key, alertRadius);
                case ALERT_TIMEOUT: return String.format(VALUE_TEMPLATE, key, alertTimeout);
                case EN_FRIENDLY_FIRE: return String.format(VALUE_TEMPLATE, key, friendlyFireEnabled);
                case DENY_NETHER_TRAVELLING: return String.format(VALUE_TEMPLATE, key, denyNetherTravelling);
                case COMPASS_UPDATES: return String.format(VALUE_TEMPLATE, key, compassUpdates);
                case COMPASS_UPDATES_PERIOD: return String.format(VALUE_TEMPLATE, key, compassUpdatesPeriod);
                case EN_VICTIM_GLOWING: return String.format(VALUE_TEMPLATE, key, enableVictimGlowing);
                case IMMUNITY_DURATION: return String.format(VALUE_TEMPLATE, key, milkGlowImmunityDuration);
                case VICTIM_START_INVENTORY: return String.format(VALUE_TEMPLATE, key, victimStartInventory);
                case HUNTER_START_INVENTORY: return String.format(VALUE_TEMPLATE, key, hunterStartInventory);
                default: return String.format(UNKNOWN_PARAMETER, key);
            }
        }
    }

    //returns true only when field changed
    private static void readValue(String key) {
        tempValues.put(key, currentCfg.get(key, defaultValues.get(key)));
    }

    private static boolean validateBool(boolean oldVal, String key) {
        boolean newVal;
        try {
            newVal = (boolean) tempValues.get(key);
        } catch (ClassCastException e) {
            newVal = (boolean) defaultValues.get(key);
        }
        if (newVal != oldVal)
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, key, newVal));
        return newVal;
    }

    private static int validateInt(int oldVal, String key, Predicate<Integer> validator) {
        int def = (int) defaultValues.get(key);
        int newVal;
        try {
            int current = (int) tempValues.get(key);
            newVal = validator.test(current) ? current : def;
        } catch (ClassCastException e) {
            newVal = def;
        }
        if (newVal != oldVal)
            Bukkit.broadcastMessage(String.format(Messages.CONFIG_MODIFIED, key, newVal));
        return newVal;
    }

    private static HashMap<Material, Integer> fillInventoryMap(ConfigurationSection cs) {
        HashMap<Material, Integer> map = new HashMap<>();
        if (cs != null) {
            for (String itemName : cs.getKeys(false)) {
                try {
                    Material m = Material.valueOf(itemName);
                    int qty = cs.getInt(itemName);
                    if (qty > 0) {
                        map.put(m, qty);
                        LoggerUtil.debug(String.format(READ_STARTINV_ENTRY, m.name(), qty));
                    }
                } catch (IllegalArgumentException e) {
                    LoggerUtil.warn(String.format(UNKNOWN_ITEM, itemName));
                }
            }
        }
        return map;
    }
}
