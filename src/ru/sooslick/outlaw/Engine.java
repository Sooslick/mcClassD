package ru.sooslick.outlaw;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.anypercent.AnyPercentBase;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;
import ru.sooslick.outlaw.util.CommonUtil;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Main Manhunt class which contains all core functionality
 */
public class Engine extends JavaPlugin {

    private static final String GAME_STATE_CHANGED = "ClassD game state has changed. New state: ";
    private static final String GAME_STATE_UNKNOWN = "Suspicious game state: ";
    private static final String GAMEMODE_ACTIVE = "Gamemode not changed, active gamemode: ";
    private static final String GAMEMODE_LOAD_CLASS = "Trying to load gamemode from ";
    private static final String GAMEMODE_LOAD_DEFAULT = "Loaded default gamemode Any%";
    private static final String GAMEMODE_LOADED = "Loaded gamemode ";
    private static final String GAMEMODE_UNLOAD = "Unload gamemode ";
    private static final String PLUGIN_CREATE_DATAFOLDER = "Created plugin data folder";
    private static final String PLUGIN_CREATE_DATAFOLDER_FAILED = "§eCannot create plugin data folder. Default config will be loaded.\n Do you have sufficient rights?";
    private static final String PLUGIN_DISABLE_SUCCESS = "Disable ClassD Plugin - success";
    private static final String PLUGIN_INIT_SUCCESS = "Init ClassD Plugin - success";
    private static final String SELECTOR_EXCLUDE = "No suggesters, choices are %s/%s online players";
    private static final String SELECTOR_ONLINE_PLAYERS = "Choosing victim through the whole online players";
    private static final String SELECTOR_SUGGESTERS = "Choosing victim from one of suggested players";

    private static final int DEFAULT_REFRESH_TIMER = 10;

    private static Engine instance;

    private List<Hunter> hunters;
    private Outlaw outlaw;
    private List<String> volunteers;
    private List<String> excludes;
    private List<String> votestarters;
    private Map<String, TimedRequest> joinRequests;
    private int votestartCountdown;
    private int votestartTimerId;
    private int gameTimerId;
    private long gameTimer;
    private int glowingRefreshTimer;
    private Location spawnLocation;
    private ScoreboardHolder scoreboardHolder;
    private GameState state;
    private GameModeBase gamemode;
    private SafeLocationsHolder safeLocationsHolder;
    private ChestTracker chestTracker;
    private StatsCollector statsCollector;

    private final Runnable votestartTimerImpl = () -> {
        if (votestartCountdown <= 0)
            changeGameState(GameState.GAME);
        else if (--votestartCountdown % 10 == 0) {
            Bukkit.broadcastMessage(String.format(Messages.START_COUNTDOWN, votestartCountdown));
        }
    };

    private final Runnable victimGlowingImpl = () -> {
        if (--glowingRefreshTimer <= 0) {
            outlaw.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, DEFAULT_REFRESH_TIMER*21, 1));
            glowingRefreshTimer = DEFAULT_REFRESH_TIMER;
        }
    };

    private final Runnable gameProcessor = () -> {
        gameTimer++;
        gamemode.tick();

        //wait for alert cooldown, then check distance between victim and hunters every second
        outlaw.huntersNearbyAlert();

        //change compass direction to actual victim's position every second
        for (Hunter h : hunters) {
            h.triggerCompassUpdateTick();
        }

        if (Cfg.enableVictimGlowing)
            victimGlowingImpl.run();

        //process join requests
        for (Map.Entry<String, TimedRequest> e : joinRequests.entrySet()) {
            if (e.getValue().tick()) {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null)
                    p.sendMessage(Messages.JOIN_REQUEST_EXPIRED);
            }
        }
    };

    @Override
    public void onEnable() {
        instance = this;

        //init working folder and config file
        if (!(getDataFolder().exists())) {
            if (getDataFolder().mkdir()) {
                LoggerUtil.info(PLUGIN_CREATE_DATAFOLDER);
                saveDefaultConfig();
            } else {
                LoggerUtil.warn(PLUGIN_CREATE_DATAFOLDER_FAILED);
            }
        }

        CommandListener cmdListener = new CommandListener();
        //register manhunt
        PluginCommand cmd = getCommand(CommandListener.COMMAND_MANHUNT);
        assert cmd != null;
        cmd.setExecutor(cmdListener);
        //register accept
        cmd = getCommand(CommandListener.COMMAND_ACCEPT_ALIAS);
        assert cmd != null;
        cmd.setExecutor(cmdListener);

        getServer().getPluginManager().registerEvents(new EventListener(), this);
        getServer().getPluginManager().registerEvents(cmdListener, this);

        //init game variables
        safeLocationsHolder = new SafeLocationsHolder();
        changeGameState(GameState.IDLE);
        LoggerUtil.info(PLUGIN_INIT_SUCCESS);

        //init metrics
        Metrics metrics = new Metrics(this, 10210);
        metrics.addCustomChart(new Metrics.SimplePie("preferred_gamemode", () -> gamemode.getName()));
    }

    @Override
    public void onDisable() {
        if (state == GameState.GAME) {
            outlaw.onEndGame();
            hunters.forEach(Hunter::onEndGame);
        }
        if (chestTracker != null) {
            chestTracker.cleanup();
        }
        LoggerUtil.info(PLUGIN_DISABLE_SUCCESS);
        LoggerUtil.info(Messages.UNPLAYABLE_WORLD_WARNING);
    }

    /**
     * Return the instance of Manhunt plugin
     * @return instance of Manhunt plugin
     */
    public static Engine getInstance() {
        return instance;
    }

    void forceStartGame(CommandSender sender) {
        if (state == GameState.GAME) {
            sender.sendMessage(Messages.GAME_IS_RUNNING);
        } else {
            votestartCountdown = 0;
            if (state == GameState.IDLE) {
                changeGameState(GameState.PRESTART);
            }
            Bukkit.broadcastMessage(String.format(Messages.START_FORCED, sender.getName()));
        }
    }

    /**
     * End the game and announce the winner
     * @param victimWin is Victim the winner
     */
    public void triggerEndgame(boolean victimWin) {
        triggerEndgame(victimWin, null);
    }

    /**
     * End the game and announce the winner
     * @param victimWin is Victim the winner
     * @param customMessage announce text
     */
    public void triggerEndgame(boolean victimWin, String customMessage) {
        String message = customMessage == null ?
                (victimWin ? Messages.VICTIM_ESCAPED : Messages.VICTIM_DEAD) :
                customMessage;
        Bukkit.broadcastMessage(message);
        outlaw.onEndGame();
        hunters.forEach(Hunter::onEndGame);
        statsCollector.scheduleBroadcast();
        changeGameState(GameState.IDLE);
    }

    void unvote(Player p) {
        String name = p.getName();
        if (volunteers.remove(name)) {
            Bukkit.broadcastMessage(String.format(Messages.VOLUNTEER_LEFT, name));
        }
        if (state == GameState.IDLE) {
            if (votestarters.remove(name)) {
                Bukkit.broadcastMessage(String.format(Messages.START_VOTES_COUNT, votestarters.size(), Cfg.minStartVotes));
            }
        }
    }

    void voteStart(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage(Messages.START_VOTE_INGAME);
            return;
        }
        String name = p.getName();
        if (votestarters.contains(name)) {
            p.sendMessage(Messages.START_VOTE_TWICE);
            return;
        }
        votestarters.add(name);
        Bukkit.broadcastMessage(String.format(Messages.START_VOTE, name));
        if (state == GameState.IDLE &&
                (votestarters.size() >= Bukkit.getOnlinePlayers().size() || votestarters.size() >= Cfg.minStartVotes)) {
            changeGameState(GameState.PRESTART);
        } else {
            Bukkit.broadcastMessage(String.format(Messages.START_VOTES_COUNT, votestarters.size(), Cfg.minStartVotes));
        }
    }

    void suggest(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage(Messages.VOLUNTEER_SUGGEST_INGAME);
            return;
        }
        String name = p.getName();
        if (volunteers.contains(name)) {
            p.sendMessage(Messages.VOLUNTEER_SUGGEST_TWICE);
            return;
        }
        volunteers.add(name);
        excludes.remove(name);
        Bukkit.broadcastMessage(String.format(Messages.VOLUNTEER_SUGGEST, name));
    }

    void exclude(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage(Messages.VOLUNTEER_SUGGEST_INGAME);
            return;
        }
        String name = p.getName();
        if (excludes.contains(name)) {
            p.sendMessage(Messages.VOLUNTEER_ALREADY_EXCLUDED);
            return;
        }
        excludes.add(name);
        volunteers.remove(name);
        Bukkit.broadcastMessage(String.format(Messages.VOLUNTEER_EXCLUDED, name));
    }

    void joinRequest(Player sender) {
        //allow command only in game
        if (state != GameState.GAME) {
            sender.sendMessage(Messages.JOIN_REQUEST_LOBBY);
            return;
        }
        //check outlaw
        if (outlaw.getPlayer().equals(sender)) {
            sender.sendMessage(Messages.JOIN_REQUEST_VICTIM);
            return;
        }
        //check hunters
        for (Hunter h : hunters) {
            if (h.getPlayer().equals(sender)) {
                sender.sendMessage(Messages.JOIN_REQUEST_HUNTER);
                return;
            }
        }
        //check requests
        for (Map.Entry<String, TimedRequest> e : joinRequests.entrySet()) {
            if (e.getKey().equals(sender.getName())) {
                sender.sendMessage(Messages.JOIN_REQUEST_EXISTS);
                return;
            }
        }
        //then create new request and alert Victim
        joinRequests.put(sender.getName(), new TimedRequest());
        sender.sendMessage(Messages.JOIN_REQUEST_SENT);
        outlaw.getPlayer().sendMessage(String.format(Messages.JOIN_REQUEST_NOTIFICATION, sender.getName()));
    }

    void acceptJoinRequest(Player sender) {
        //allow command only in game
        if (state != GameState.GAME) {
            sender.sendMessage(Messages.GAME_IS_NOT_RUNNING);
            return;
        }
        //allow only for outlaw
        if (!sender.equals(outlaw.getPlayer())) {
            sender.sendMessage(Messages.ONLY_VICTIM_ALLOWED);
            return;
        }
        //accept all active requests
        int acceptedRequests = 0;
        for (Map.Entry<String, TimedRequest> e : joinRequests.entrySet()) {
            TimedRequest req = e.getValue();
            if (req.isActive()) {
                req.deactivate();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) {
                    joinHunter(p);
                    acceptedRequests++;
                    Bukkit.broadcastMessage(String.format(Messages.JOIN_REQUEST_ACCEPTED, p.getName()));
                    scoreboardHolder.recalculateNametagVisiblity(hunters.size());
                }
            }
        }
        //apply handicap potion effects if there are accepted requests
        if (acceptedRequests > 0) {
            if (Cfg.enablePotionHandicap)
                applyPotionHandicap(sender, 400);
        } else {
            sender.sendMessage(Messages.JOIN_REQUEST_NOT_EXISTS);
        }
    }

    /**
     * Return the instance of currently loaded gamemode
     * @return current Manhunt's gamemode
     */
    public GameModeBase getGameMode() {
        return gamemode;
    }

    /**
     * Return the state of the game
     * @return current game state
     */
    public GameState getGameState() {
        return state;
    }

    /**
     * Return the current Victim
     * @return Victim or null if the game is not running
     */
    public Outlaw getOutlaw() {
        return outlaw;
    }

    /**
     * Return the list of active Hunters
     * @return list of active Hunters
     */
    public List<Hunter> getHunters() {
        return hunters;
    }

    /**
     * Search the Hunter by player
     * @param p player
     * @return Hunter or null if this player is not a Hunter
     */
    public Hunter getHunter(Player p) {
        //comparing by name
        return hunters.stream()
                .filter(h -> h.getPlayer().getName().equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Return amount of seconds passed since the game start
     * @return amount of seconds since the start
     */
    public long getGameTimer() {
        return gameTimer;
    }

    /**
     * Return the current ChestTracker
     * @return current ChestTracker
     */
    public ChestTracker getChestTracker() {
        return chestTracker;
    }

    /**
     * Return the current ScoreboardHolder
     * @return current ScoreboardHolder
     */
    public ScoreboardHolder getScoreboardHolder() {
        return scoreboardHolder;
    }

    StatsCollector getStatsCollector() {
        return statsCollector;
    }

    void setGlowingRefreshTimer(int glowingRefreshTimer) {
        this.glowingRefreshTimer = glowingRefreshTimer;
    }

    private void changeGameState(GameState state) {
        this.state = state;
        LoggerUtil.info(GAME_STATE_CHANGED + state.toString());
        switch (state) {
            case IDLE:
                //stop game and read cfg
                Bukkit.getScheduler().cancelTask(gameTimerId);
                reloadConfig();
                Cfg.readConfig(getConfig());
                reloadGamemode();
                Cfg.readGameModeConfig(gamemode);

                //reinit game variables
                votestarters = new ArrayList<>();
                excludes = new ArrayList<>();
                volunteers = new ArrayList<>();
                hunters = new ArrayList<>();
                joinRequests = new HashMap<>();
                votestartCountdown = Cfg.prestartTimer;
                gameTimer = 0;
                glowingRefreshTimer = 0;

                //launch spawns finder
                safeLocationsHolder.launchJob();

                //reset players gamemode
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);
                gamemode.onIdle();
                break;
            case PRESTART:
                //removes created or found containers and beds
                if (chestTracker != null)
                    chestTracker.cleanup();
                chestTracker = new ChestTracker();

                gamemode.onPreStart();

                //launch timer
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage(String.format(Messages.START_COUNTDOWN, votestartCountdown));
                break;
            case GAME:
                Supplier<Location> victimSupplier;
                Supplier<Location> hunterSupplier;
                if (gamemode.customSpawnEnabled()) {
                    victimSupplier = gamemode::getVictimSpawn;
                    hunterSupplier = gamemode::getHunterSpawn;
                } else {
                    safeLocationsHolder.selectSafeLocations();
                    victimSupplier = safeLocationsHolder::getVictimLocation;
                    hunterSupplier = safeLocationsHolder::getHunterLocation;
                }
                Bukkit.getScheduler().cancelTask(votestartTimerId);
                scoreboardHolder = new ScoreboardHolder(Bukkit.getScoreboardManager());
                statsCollector = new StatsCollector();

                //prepare environment
                World w = Bukkit.getWorlds().get(0);
                w.setTime(0);
                w.setStorm(false);

                //filter excludes
                Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
                if (volunteers.isEmpty()) {
                    onlinePlayers.forEach(p -> {
                        String name = p.getName();
                        if (!excludes.contains(name))
                            volunteers.add(name);
                    });
                    LoggerUtil.debug(String.format(SELECTOR_EXCLUDE, volunteers.size(), onlinePlayers.size()));
                }

                //select outlaw entity
                Player selectedPlayer;
                if (volunteers.isEmpty()) {
                    selectedPlayer = CommonUtil.getRandomOf(onlinePlayers);
                    LoggerUtil.debug(SELECTOR_ONLINE_PLAYERS);
                } else {
                    //noinspection ConstantConditions
                    selectedPlayer = Bukkit.getPlayer(CommonUtil.getRandomOf(volunteers));
                    LoggerUtil.debug(SELECTOR_SUGGESTERS);
                }
                //validate selected player
                if (selectedPlayer == null) {
                    LoggerUtil.warn("No players online. Skipping GAME state and going to IDLE");
                    changeGameState(GameState.IDLE);
                    return;
                }
                scoreboardHolder.addVictim(selectedPlayer);
                Bukkit.broadcastMessage(String.format(Messages.SELECTED_VICTIM, selectedPlayer.getName()));

                //process outlaw
                outlaw = new Outlaw(selectedPlayer);
                Location outlawLocation = victimSupplier.get();
                outlaw.preparePlayer(outlawLocation);
                //give handicap effects
                if (Cfg.enablePotionHandicap) {
                    applyPotionHandicap(selectedPlayer);
                }

                //process others
                Hunter.setupHunter(outlaw);
                spawnLocation = hunterSupplier.get();
                Bukkit.getWorlds().get(0).setSpawnLocation(spawnLocation);     //for new players and respawns
                for (Player p : onlinePlayers) {
                    //skip outlaw
                    if (p.equals(selectedPlayer))
                        continue;
                    //add others to hunter team
                    joinHunter(p);
                }

                //set nametag visiblity
                scoreboardHolder.recalculateNametagVisiblity(hunters.size());

                gamemode.onGame();
                Bukkit.broadcastMessage(String.format(Messages.SELECTED_OBJECTIVE, gamemode.getObjective()));

                //debug: check distance btw runner and hunters
                if (hunters.size() > 0) {
                    Bukkit.broadcastMessage(String.format(Messages.SELECTED_HANDICAP,
                            Math.round(WorldUtil.distance2d(outlaw.getLocation(), hunters.get(0).getLocation()))));
                }

                //run game
                gameTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, gameProcessor, 1, 20);
                Bukkit.broadcastMessage(Messages.GAME_STARTED);
                break;
            default:
                LoggerUtil.warn(GAME_STATE_UNKNOWN + state.toString());
        }
    }

    private void reloadGamemode() {
        if (gamemode == null || !gamemode.getClass().equals(Cfg.preferredGamemode)) {
            boolean unload = false;
            //unload previous
            if (gamemode != null) {
                LoggerUtil.debug(GAMEMODE_UNLOAD + gamemode.getName());
                gamemode.unload();
                unload = true;
            }
            //try to load new gamemode
            try {
                LoggerUtil.debug(GAMEMODE_LOAD_CLASS + Cfg.preferredGamemode);
                gamemode = Cfg.preferredGamemode.getDeclaredConstructor().newInstance();
                LoggerUtil.debug(GAMEMODE_LOADED + gamemode.getName());
            }
            //cannot load gamemode, load default AnyPercent
            catch (Exception e) {
                LoggerUtil.warn(e.getMessage());
                gamemode = new AnyPercentBase();
                LoggerUtil.debug(GAMEMODE_LOAD_DEFAULT);
            }
            if (unload)
                Bukkit.broadcastMessage(String.format(Messages.GAMEMODE_CHANGED, gamemode.getName()));
            return;
        }
        //log string if nothing changed
        LoggerUtil.debug(GAMEMODE_ACTIVE + gamemode.getName());
    }

    private void joinHunter(Player p) {
        Hunter currentHunter = new Hunter(p);
        hunters.add(currentHunter);
        currentHunter.preparePlayer(spawnLocation);
        scoreboardHolder.addHunter(p);
    }

    private void applyPotionHandicap(LivingEntity selectedPlayer) {
        int x = Bukkit.getOnlinePlayers().size();
        applyPotionHandicap(selectedPlayer, (int) ((x * x / 5 + 0.8) * 400));
    }

    private void applyPotionHandicap(LivingEntity selectedPlayer, int duration) {
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, duration, 1));
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration, 1));
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, duration, 1));
    }
}
