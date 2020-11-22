package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class Engine extends JavaPlugin {

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
    private int alertTimeoutTimer;
    private boolean hunterAlert;
    private int halfSize;
    private int escapeArea;
    private Scoreboard scoreboard;
    private GameState state;
    private EventListener eventListener;
    private Logger log;
    private TimedMessages timedMessages;
    private ChestTracker chestTracker;

    private final Runnable votestartTimerImpl = () -> {
        if (--votestartCountdown % 10 == 0) {
            if (votestartCountdown <= 0)
                changeGameState(GameState.GAME);
            else
                Bukkit.broadcastMessage("§c" + votestartCountdown + " seconds to start");
        }
    };

    private final Runnable gameProcessor = () -> {
        gameTimer++;

        //wait for alert cooldown, then check distance between victim and hunters every second
        if (alertTimeoutTimer > 0)
            alertTimeoutTimer--;
        else
            alertOutlaw();

        //change compass direction to actual victim's position every second
        for (Hunter h : hunters) {
            h.updateCompass(outlaw);
        }

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
                    p.sendMessage("Your request have been expired");
            }
        }
    };

    @Override
    public void onEnable() {
        //init working folder and config file
        log = Bukkit.getLogger();
        log.info("Init Class D Plugin");
        if (!(getDataFolder().exists())) {
            if (getDataFolder().mkdir()) {
                log.info("Created plugin data folder");
                saveDefaultConfig();
            } else {
                log.warning("§eCannot create plugin data folder. Default config will be loaded.\n Do you have correct rights?");
            }
        }

        //register commands and events
        Wall.setEngine(this);
        CommandListener cmdListener = new CommandListener(this);
        getCommand("manhunt").setExecutor(cmdListener);
        getCommand("y").setExecutor(cmdListener);
        eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);

        //init game variables
        changeGameState(GameState.IDLE);
        timedMessages = new TimedMessages(this).launch();
        log.info("Init Class D Plugin - success");
    }

    @Override
    public void onDisable() {
        if (chestTracker != null) {
            chestTracker.cleanupBlocks();
            chestTracker.cleanupEntities();
        }
    }

    public void voteStart(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage("§cCannot votestart while game is running");
            return;
        }
        String name = p.getName();
        if (votestarters.contains(name)) {
            p.sendMessage("§cCannot votestart twice");
            return;
        }
        votestarters.add(name);
        Bukkit.broadcastMessage("§e" + name + " voted to start game");
        if (votestarters.size() >= Cfg.minStartVotes && state == GameState.IDLE) {
            changeGameState(GameState.PRESTART);
            return;
        }
    }

    public void suggest(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage("§cCannot suggest while game is running");
            return;
        }
        String name = p.getName();
        if (volunteers.contains(name)) {
            p.sendMessage("§cCannot suggest twice");
            return;
        }
        volunteers.add(name);
        Bukkit.broadcastMessage("§e" + name + " suggested yourself as Victim");
    }

    public void joinRequest(Player sender) {
        //allow command only in game
        if (state != GameState.GAME) {
            sender.sendMessage("§cGame is not running, use §e/outlaw votestart §cinstead");
            return;
        }
        //check outlaw
        if (outlaw.getPlayer().equals(sender)) {
            sender.sendMessage("§cYou are Victim. Nice try :P");
            return;
        }
        //check hunters
        for (Hunter h : hunters) {
            if (h.getPlayer().equals(sender)) {
                sender.sendMessage("§cYou are Hunter");
                return;
            }
        }
        //check requests
        for (Map.Entry<String, TimedRequest> e : joinRequests.entrySet()) {
            if (e.getKey().equals(sender.getName())) {
                sender.sendMessage("§cYou have sent the request yet");
                return;
            }
        }
        //then create new request and alert Victim
        joinRequests.put(sender.getName(), new TimedRequest());
        sender.sendMessage("§cJoin request has sent");
        outlaw.getPlayer().sendMessage("§e" + sender.getName() + " §cwant to join the game. Type §e/y §c to accept or just ignore him");
    }

    public void acceptJoinRequest(Player sender) {
        //allow command only in game
        if (state != GameState.GAME) {
            sender.sendMessage("§cGame is not running.");
            return;
        }
        //allow only for outlaw
        if (!sender.equals(outlaw.getPlayer())) {
            sender.sendMessage("§cOnly Victim can use this command");
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
                    //todo copypasted from changeGameState - Game, refactor to method
                    Hunter currentHunter = new Hunter(p);
                    hunters.add(currentHunter);
                    currentHunter.preparePlayer(Bukkit.getWorlds().get(0).getSpawnLocation());
                    p.getInventory().addItem(new ItemStack(Material.COMPASS));

                    acceptedRequests++;
                    Bukkit.broadcastMessage("§e" + p.getName() + " §cjoined the game as Hunter");
                }
            }
        }
        //apply handicap potion effects if there are accepted requests
        if (acceptedRequests > 0) {
            if (Cfg.enablePotionHandicap)
                applyPotionHandicap(sender, 400);
        } else {
            sender.sendMessage("§cYou have no join requests");
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
        for (Hunter h : hunters) {
            if (h.getPlayer().equals(p))
                return h;
        }
        return null;
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

    protected void changeGameState(GameState state) {
        this.state = state;
        log.info("Outlaw game state changed. New state: " + state.toString());
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
                alertTimeoutTimer = 0;
                gameTimer = 0;
                killCounter = 0;
                hunterAlert = false;
                halfSize = Cfg.playzoneSize / 2 + 1;
                escapeArea = halfSize + Cfg.wallThickness + 1;  //todo: +/- bug

                //reset players gamemode and achievements
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);

                //regenerate wall
                if (Cfg.enableEscapeGamemode) {
                    Wall.buildWall();
                    Bukkit.broadcastMessage("§cPlz wait until the rebuild process is complete...");
                } else {
                    Bukkit.broadcastMessage("§eReady for the next game");
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
                Bukkit.broadcastMessage(Cfg.prestartTimer + " seconds to launch");
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
                scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();          //todo NPE check
                Team teamVictim = scoreboard.registerNewTeam("Victim");
                Team teamHunter = scoreboard.registerNewTeam("Hunter");

                //prepare environment
                World w = Bukkit.getWorlds().get(0);
                w.setTime(0);
                w.setStorm(false);

                //select outlaw entity
                if (volunteers.isEmpty()) {
                    selectedPlayer = Util.getRandomOf(onlinePlayers);
                } else {
                    selectedPlayer = Bukkit.getPlayer(Util.getRandomOf(volunteers));
                }
                Bukkit.broadcastMessage("§eSelected Victim: §c" + selectedPlayer.getName());

                //process outlaw
                outlaw = new Outlaw(selectedPlayer);
                Location outlawLocation = Util.getSafeRandomLocation(Cfg.spawnRadius);
                outlaw.preparePlayer(outlawLocation);
                //give handicap effects
                if (Cfg.enablePotionHandicap) {
                    applyPotionHandicap(selectedPlayer);
                }
                //join to team and hide nametag
                selectedPlayer.setScoreboard(scoreboard);
                teamVictim.addEntry(selectedPlayer.getName());
                if (Bukkit.getOnlinePlayers().size() >= Cfg.hideVictimNametagAbovePlayers) {
                    Bukkit.broadcastMessage("§eVictim's nametag is invisible");
                    teamVictim.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                }

                //process others
                Location hunterLocation = Util.getSafeDistanceLocation(outlawLocation, Cfg.spawnDistance);
                Bukkit.getWorlds().get(0).setSpawnLocation(hunterLocation);
                for (Player p : onlinePlayers) {
                    //skip outlaw
                    if (p.equals(selectedPlayer))
                        continue;
                    //add others to hunter team
                    Hunter currentHunter = new Hunter(p);
                    hunters.add(currentHunter);
                    currentHunter.preparePlayer(hunterLocation);
                    p.setScoreboard(scoreboard);
                    teamHunter.addEntry(p.getName());
                }

                if (Cfg.enableEscapeGamemode) {
                    Bukkit.broadcastMessage("§eVictim's target: §cESCAPE");
                } else {
                    Bukkit.broadcastMessage("§eVictim's target: §cKILL DRAGON");
                }

                //debug: check distance btw runner and hunters
                if (hunters.size() > 0) {
                    Bukkit.broadcastMessage("§eSpawn handicap: " + Util.distance(outlaw.getLocation(), hunters.get(0).getLocation()));
                }

                //run game
                gameTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, gameProcessor, 1, 20);
                Bukkit.broadcastMessage("§eGame started. Run!");
                break;
            default:
                log.warning("Game state is not implemented: " + state.toString());
        }
    }

    private void alertOutlaw() {
        LivingEntity outlawPlayer = outlaw.getEntity();
        Location outlawLocation = outlawPlayer.getLocation();
        for (Hunter h : hunters) {
            if (!h.getPlayer().getWorld().equals(outlawLocation.getWorld()))
                continue;
            if (Util.distance(h.getLocation(), outlawLocation) < Cfg.alertRadius) {
                alertTimeoutTimer = Cfg.alertTimeout;
                outlawPlayer.sendMessage("§cHunters nearby");
                //glow placeholder entity if Outlaw player is offline
                if (!(outlawPlayer instanceof Player)) {
                    outlawPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Cfg.alertTimeout * 20, 3));
                }
                break;
            }
        }
    }

    private void alertHunter() {
        Location l = outlaw.getLocation();
        if (isOutside(l)) {
            hunterAlert = true;
            Bukkit.broadcastMessage("§cVictim is breaking through the Wall");
        }
    }

    private void checkEscape() {
        Location l = outlaw.getLocation();
        if ((Math.abs(l.getX()) > escapeArea) || (Math.abs(l.getZ()) > escapeArea)) {
            Bukkit.broadcastMessage("§eVictim escaped!");
            changeGameState(GameState.IDLE);
        }
    }

    public boolean isOutside(Location l) {
        return ((Math.abs(l.getX()) > halfSize + 1) || (Math.abs(l.getZ()) > halfSize + 1) || l.getY() > 255);
    }

    private void applyPotionHandicap(LivingEntity selectedPlayer) {
        int x = Bukkit.getOnlinePlayers().size();
        applyPotionHandicap(selectedPlayer, (int) ((x*x/5 + 0.8) * 400));
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
    //  rm debugmode param and debug outputs
    //  countdown gamemode
    //  victim glowing param
    //  wall progbar feature

    //todo: re-organize gamemodes impl

    //todo debug: check all location.add() usages
}
