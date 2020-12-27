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

    private static Engine instance;

    private List<Hunter> hunters;
    private Outlaw outlaw;
    private List<String> volunteers;
    private List<String> votestarters;
    private Map<String, TimedRequest> joinRequests;
    private int votestartCountdown;
    private int votestartTimerId;
    private int gameTimerId;
    private long gameTimer;
    private int killCounter;
    private boolean hunterAlert;
    private int halfSize;
    private int escapeArea;
    private Location spawnLocation;
    private ScoreboardHolder scoreboardHolder;
    private GameState state;
    private EventListener eventListener;
    private TimedMessages timedMessages;
    private ChestTracker chestTracker;

    private final Runnable votestartTimerImpl = () -> {
        if (votestartCountdown <= 0)
            changeGameState(GameState.GAME);
        else if (--votestartCountdown % 10 == 0) {
            Bukkit.broadcastMessage(String.format(Messages.START_COUNTDOWN, votestartCountdown));
        }
    };

    private final Runnable gameProcessor = () -> {
        gameTimer++;

        //wait for alert cooldown, then check distance between victim and hunters every second
        outlaw.huntersNearbyAlert();

        //change compass direction to actual victim's position every second
        for (Hunter h : hunters) {
            h.updateCompass(outlaw);
        }

        //todo: move wall methods to gamemode
        //check if Victim's position is inside or outside the wall
        if (Cfg.enableEscapeGamemode) {
            if (!hunterAlert)
                alertHunter();
            checkEscape();
        }

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
        changeGameState(GameState.IDLE);
        timedMessages = new TimedMessages().launch();
        LoggerUtil.info(PLUGIN_INIT_SUCCESS);
    }

    @Override
    public void onDisable() {
        LoggerUtil.info(PLUGIN_DISABLE);
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
        //send message
        Bukkit.broadcastMessage(victimWin ? Messages.VICTIM_ESCAPED : Messages.VICTIM_DEAD);
        //todo: foreach players - endGameTrigger. InvToChest in abstract player + placeholder cleanup in outlaw
        //create inventory chest for Victim
        WorldUtil.invToChest(outlaw.getPlayer().getInventory(), outlaw.getEntity().getLocation());
        //create inventory chests for hunters
        hunters.forEach(h -> WorldUtil.invToChest(h.getPlayer().getInventory(), h.getEntity().getLocation()));
        //finally change game state
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
        Bukkit.broadcastMessage(String.format(Messages.VOLUNTEER_SUGGEST, name));
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

    public int getKillCounter() {
        return killCounter;
    }

    public void incKill() {
        killCounter++;
    }

    public int getHalfSize() {
        return halfSize;
    }

    public ChestTracker getChestTracker() {
        return chestTracker;
    }

    public ScoreboardHolder getScoreboardHolder() {
        return scoreboardHolder;
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

                //reinit game variables
                votestarters = new ArrayList<>();
                volunteers = new ArrayList<>();
                hunters = new ArrayList<>();
                joinRequests = new HashMap<>();
                votestartCountdown = Cfg.prestartTimer;
                gameTimer = 0;
                killCounter = 0;
                hunterAlert = false;
                //todo move gamemode's variables to gamemode
                halfSize = Cfg.playzoneSize / 2 + 1;
                escapeArea = halfSize + Cfg.wallThickness;

                //reset players gamemode
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);

                //regenerate wall
                if (Cfg.enableEscapeGamemode) {
                    Wall.buildWall();
                    Bukkit.broadcastMessage("§cPlease wait until the Wall is rebuilt"); //todo mooveee
                } else {
                    Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
                }
                break;
            case PRESTART:
                //removes created or found containers and beds
                if (chestTracker != null)
                    chestTracker.cleanupBlocks();

                //generate wall spots
                if (Cfg.enableEscapeGamemode) {
                    Wall.buildSpots();
                }

                //launch timer
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage(String.format(Messages.START_COUNTDOWN, votestartCountdown));
                break;
            case GAME:
                //reinit variables and stop lobby timers
                if (chestTracker != null)
                    chestTracker.cleanupEntities();     //separated cleanups due to beds dropping while blocks cleanup
                chestTracker = new ChestTracker();
                eventListener.reset();
                Bukkit.getScheduler().cancelTask(votestartTimerId);
                Player selectedPlayer;
                Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
                scoreboardHolder = new ScoreboardHolder(Bukkit.getScoreboardManager());

                //prepare environment
                World w = Bukkit.getWorlds().get(0);
                w.setTime(0);
                w.setStorm(false);

                //select outlaw entity
                if (volunteers.isEmpty()) {
                    selectedPlayer = CommonUtil.getRandomOf(onlinePlayers);
                } else {
                    selectedPlayer = Bukkit.getPlayer(CommonUtil.getRandomOf(volunteers));
                }
                scoreboardHolder.addVictim(selectedPlayer);
                Bukkit.broadcastMessage(String.format(Messages.SELECTED_VICTIM, selectedPlayer.getName()));

                //process outlaw
                outlaw = new Outlaw(selectedPlayer);
                Location outlawLocation = WorldUtil.getSafeRandomLocation(Cfg.spawnRadius);
                outlaw.preparePlayer(outlawLocation);
                //give handicap effects
                if (Cfg.enablePotionHandicap) {
                    applyPotionHandicap(selectedPlayer);
                }

                //process others
                spawnLocation = WorldUtil.getSafeDistanceLocation(outlawLocation, Cfg.spawnDistance);
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

                //todo: GAMEMODE.GETOBJECTIVE()
                String objective = Cfg.enableEscapeGamemode ? "ESCAPE" : "KILL ENDER DRAGON";
                Bukkit.broadcastMessage(String.format(Messages.SELECTED_OBJECTIVE, objective));

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

    private void joinHunter(Player p) {
        Hunter currentHunter = new Hunter(p);
        hunters.add(currentHunter);
        currentHunter.preparePlayer(spawnLocation);
        scoreboardHolder.addHunter(p);
    }

    //todo: gamemode
    private void alertHunter() {
        Location l = outlaw.getLocation();
        if (isOutside(l)) {
            hunterAlert = true;
            Bukkit.broadcastMessage("§cVictim is breaking through the Wall");
        }
    }

    //todo: gamemode
    private void checkEscape() {
        Location l = outlaw.getLocation().add(-0.5, 0, -0.5);
        if ((Math.abs(l.getX()) > escapeArea) || (Math.abs(l.getZ()) > escapeArea)) {
            triggerEndgame(true);
        }
    }

    public boolean isOutside(Location l) {
        return ((Math.abs(l.getX()) > halfSize + 1) || (Math.abs(l.getZ()) > halfSize + 1) || l.getY() > 255);
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

    //todo refactor wall methods from Engine

    //todo
    //  refactor code
    //  more stats
    //  countdown gamemode
    //  victim glowing param
    //  wall progbar feature

    //todo: re-organize gamemodes impl
}
