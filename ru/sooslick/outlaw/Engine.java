package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
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

public class Engine extends JavaPlugin {

    private static final String GAME_STATE_CHANGED = "ClassD game state has changed. New state: ";
    private static final String GAME_STATE_UNKNOWN = "Suspicious game state: ";
    private static final String PLUGIN_CREATE_DATAFOLDER = "Created plugin data folder";
    private static final String PLUGIN_CREATE_DATAFOLDER_FAILED = "§eCannot create plugin data folder. Default config will be loaded.\n Do you have sufficient rights?";
    private static final String PLUGIN_DISABLE = "Disable ClassD Plugin";
    private static final String PLUGIN_DISABLE_SUCCESS = "Disable ClassD Plugin - success";
    private static final String PLUGIN_INIT = "Init ClassD Plugin";
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
    private int killCounter;
    private int glowingRefreshTimer;
    private Location spawnLocation;
    private ScoreboardHolder scoreboardHolder;
    private GameState state;
    private GameModeBase gamemode;
    private EventListener eventListener;
    private SafeLocationsHolder safeLocationsHolder;
    private ChestTracker chestTracker;

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
        LoggerUtil.info(PLUGIN_INIT);
        if (!(getDataFolder().exists())) {
            if (getDataFolder().mkdir()) {
                LoggerUtil.info(PLUGIN_CREATE_DATAFOLDER);
                saveDefaultConfig();
            } else {
                LoggerUtil.warn(PLUGIN_CREATE_DATAFOLDER_FAILED);
            }
        }

        //register commands and events
        CommandListener cmdListener = new CommandListener();
        getCommand(CommandListener.COMMAND_MANHUNT).setExecutor(cmdListener);
        getCommand(CommandListener.COMMAND_ACCEPT_ALIAS).setExecutor(cmdListener);
        eventListener = new EventListener();
        getServer().getPluginManager().registerEvents(eventListener, this);

        //init game variables
        safeLocationsHolder = new SafeLocationsHolder();
        changeGameState(GameState.IDLE);
        LoggerUtil.info(PLUGIN_INIT_SUCCESS);
    }

    @Override
    public void onDisable() {
        LoggerUtil.info(PLUGIN_DISABLE);
        if (state == GameState.GAME) {
            outlaw.onEndGame();
            hunters.forEach(Hunter::onEndGame);
        }
        if (chestTracker != null) {
            chestTracker.cleanupBlocks();
            chestTracker.cleanupEntities();
        }
        LoggerUtil.info(PLUGIN_DISABLE_SUCCESS);
    }

    public static Engine getInstance() {
        return instance;
    }

    public void forceStartGame(CommandSender sender) {
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

    public void triggerEndgame(boolean victimWin) {
        Bukkit.broadcastMessage(victimWin ? Messages.VICTIM_ESCAPED : Messages.VICTIM_DEAD);
        outlaw.onEndGame();
        hunters.forEach(Hunter::onEndGame);
        changeGameState(GameState.IDLE);
    }

    public void unvote(Player p) {
        String name = p.getName();
        if (volunteers.remove(name)) {
            Bukkit.broadcastMessage(String.format(Messages.VOLUNTEER_LEFT, name));
        }
        if (state == GameState.IDLE) {
            if (votestarters.remove(name)) {
                Bukkit.broadcastMessage(String.format(Messages.START_VOTES_COUNT, votestarters.size(), Cfg.minStartVotes));
            }
            //todo same code in voteStart. Refactor to method
            if (votestarters.size() >= Bukkit.getOnlinePlayers().size()) {
                changeGameState(GameState.PRESTART);
            }
        }
    }

    public void voteStart(Player p) {
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

    public void suggest(Player p) {
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

    public void exclude(Player p) {
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

    public void joinRequest(Player sender) {
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

    public void acceptJoinRequest(Player sender) {
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

    public GameModeBase getGameMode() {
        return gamemode;
    }

    public GameState getGameState() {
        return state;
    }

    public Outlaw getOutlaw() {
        return outlaw;
    }

    public List<Hunter> getHunters() {
        return hunters;
    }

    public Hunter getHunter(Player p) {
        return hunters.stream().filter(h -> h.getPlayer().equals(p)).findFirst().orElse(null);
    }

    public long getGameTimer() {
        return gameTimer;
    }

    public void incKill() {
        killCounter++;
        Bukkit.broadcastMessage(String.format(Messages.DEATH_COUNTER, killCounter));
    }

    public ChestTracker getChestTracker() {
        return chestTracker;
    }

    public ScoreboardHolder getScoreboardHolder() {
        return scoreboardHolder;
    }

    public void setGlowingRefreshTimer(int glowingRefreshTimer) {
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
                killCounter = 0;

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
                    chestTracker.cleanupBlocks();

                gamemode.onPreStart();

                //launch timer
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage(String.format(Messages.START_COUNTDOWN, votestartCountdown));
                break;
            case GAME:
                //reinit variables and stop lobby timers
                if (chestTracker != null)
                    chestTracker.cleanupEntities();     //separated cleanups due to beds dropping while blocks cleanup
                chestTracker = new ChestTracker();
                safeLocationsHolder.selectSafeLocations();
                Bukkit.getScheduler().cancelTask(votestartTimerId);
                scoreboardHolder = new ScoreboardHolder(Bukkit.getScoreboardManager());

                //prepare environment
                World w = Bukkit.getWorlds().get(0);
                w.setTime(0);
                w.setStorm(false);

                //zero players bugfix: skip game if no players online
                Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
                if (onlinePlayers.size() <= 0) {
                    changeGameState(GameState.IDLE);
                    return;
                }

                //filter excludes
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
                    selectedPlayer = Bukkit.getPlayer(CommonUtil.getRandomOf(volunteers));
                    LoggerUtil.debug(SELECTOR_SUGGESTERS);
                }
                scoreboardHolder.addVictim(selectedPlayer);
                Bukkit.broadcastMessage(String.format(Messages.SELECTED_VICTIM, selectedPlayer.getName()));

                //process outlaw
                outlaw = new Outlaw(selectedPlayer);
                Location outlawLocation = safeLocationsHolder.getVictimLocation();
                outlaw.preparePlayer(outlawLocation);
                //give handicap effects
                if (Cfg.enablePotionHandicap) {
                    applyPotionHandicap(selectedPlayer);
                }

                //process others
                Hunter.setupHunter(outlaw);
                spawnLocation = safeLocationsHolder.getHunterLocation();
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
                            WorldUtil.distance2d(outlaw.getLocation(), hunters.get(0).getLocation())));
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
            //unload previous
            if (gamemode != null) {
                LoggerUtil.debug("Unload gamemode " + gamemode.getName());
                gamemode.unload();
            }
            //try to load new gamemode
            try {
                LoggerUtil.debug("Trying to load gamemode from class " + Cfg.preferredGamemode);
                gamemode = Cfg.preferredGamemode.newInstance();
                LoggerUtil.debug("Loaded gamemode " + gamemode.getName());
            }
            //cannot load gamemode, load default AnyPercent
            catch (Exception e) {
                LoggerUtil.warn(e.getMessage());
                gamemode = new AnyPercentBase();
                LoggerUtil.debug("Loaded default gamemode Any%");
            }
            return;
        }
        //log string if nothing changed
        LoggerUtil.debug("Gamemode not changed, active gamemode: " + gamemode.getName());
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

    //todo
    //  refactor code
    //  more stats
    //  countdown gamemode
    //  strings & messages 2
}
