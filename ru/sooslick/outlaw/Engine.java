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
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

public class Engine extends JavaPlugin {

    private List<Hunter> hunters;
    private Outlaw outlaw;
    private List<String> volunteers;
    private List<String> votestarters;
    private int votestartCountdown;
    private int votestartTimerId;
    private int gameTimerId;
    private long gameTimer;
    private int killCounter;
    private int alertTimeoutTimer;
    private boolean hunterAlert;
    private int halfSize;
    private int escapeArea;
    private GameState state;
    private CommandListener cmdListener;
    private EventListener eventListener;
    private Logger log;
    private TimedMessages timedMessages;

    private final Runnable votestartTimerImpl = () -> {
        votestartCountdown--;
        if (votestartCountdown % 10 == 0) {
            Bukkit.broadcastMessage("§c" + votestartCountdown + " seconds to start");
        }
        if (votestartCountdown <= 0) {
            changeGameState(GameState.GAME);
        }
    };

    private Runnable gameProcessor = () -> {
        gameTimer++;
        if (alertTimeoutTimer > 0)
            alertTimeoutTimer--;
        else
            alertOutlaw();
        updateCompass();
        if (Cfg.enableEscapeGamemode) {
            if (!hunterAlert)
                alertHunter();
            checkEscape();
        }
    };

    @Override
    public void onEnable() {
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
        changeGameState(GameState.IDLE);
        timedMessages = new TimedMessages(this).launch();
        cmdListener = new CommandListener(this);
        getCommand("outlaw").setExecutor(cmdListener);
        getCommand("y").setExecutor(cmdListener);
        eventListener = new EventListener(this);
        getServer().getPluginManager().registerEvents(eventListener, this);
        log.info("Init Class D Plugin - success");
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
        if (votestarters.size() >= Cfg.minVotestarters && state == GameState.IDLE) {
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

    public GameState getGameState() {
        return state;
    }

    public Outlaw getOutlaw() {
        return outlaw;
    }

    public List<Hunter> getHunters() {
        return hunters;
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

    protected void changeGameState(GameState state) {
        this.state = state;
        log.info("Outlaw game state changed. New state: " + state.toString());
        switch (state) {
            case IDLE:
                Bukkit.getScheduler().cancelTask(gameTimerId);
                reloadConfig();
                Cfg.readConfig(getConfig());
                votestarters = new ArrayList<>();
                volunteers = new ArrayList<>();
                hunters = new ArrayList<>();
                votestartCountdown = Cfg.votestartTimer;
                alertTimeoutTimer = 0;
                gameTimer = 0;
                killCounter = 0;
                hunterAlert = false;
                halfSize = Cfg.playzoneSize / 2;
                escapeArea = halfSize + Cfg.wallThickness + 1;
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke @a everything");
                if (Cfg.enableEscapeGamemode) {
                    Wall.generate(this);
                } else {
                    Bukkit.broadcastMessage("§eReady to next game");
                }
                break;
            case PRESTART:
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage(Cfg.votestartTimer + " seconds to launch");
                break;
            case GAME:
                eventListener.reset();
                Bukkit.getScheduler().cancelTask(votestartTimerId);
                Player selectedPlayer;
                Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

                //prepare environment
                Bukkit.getWorlds().get(0).setTime(0);

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
                preparePlayer(selectedPlayer, outlawLocation);
                //give handicap effects
                if (Cfg.enablePotionHandicap) {
                    applyPotionHandicap(selectedPlayer);
                }

                //process others
                Location hunterLocation = Util.getSafeDistanceLocation(outlawLocation, Cfg.spawnDistance);
                Bukkit.getWorlds().get(0).setSpawnLocation(hunterLocation);
                for (Player p : onlinePlayers) {
                    //skip outlaw
                    if (p.equals(selectedPlayer))
                        continue;
                    //add others to hunter team
                    Hunter h = new Hunter(p);
                    hunters.add(h);
                    preparePlayer(p, hunterLocation);
                    p.getInventory().addItem(new ItemStack(Material.COMPASS));
                }

                if (Cfg.enableEscapeGamemode) {
                    Bukkit.broadcastMessage("§eVictim's target: §cESCAPE");
                } else {
                    Bukkit.broadcastMessage("§eVictim's target: §cKILL DRAGON");
                }

                //debug: check distance btw runner and hunters
                if (hunters.size() > 0) {
                    Bukkit.broadcastMessage("§eSpawn handicap: " + Util.distance(outlaw.getPlayer().getLocation(), hunters.get(0).getPlayer().getLocation()));
                }

                //run game
                gameTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, gameProcessor, 1, 20);
                Bukkit.broadcastMessage("§eGame started. Run!");
                break;
            default:
                log.warning("Game state is not implemented: " + state.toString());
        }
    }

    private void preparePlayer(Player p, Location dest) {
        p.teleport(dest);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setExhaustion(0);
        p.setTotalExperience(0);
        p.getInventory().clear();
        p.getActivePotionEffects().clear();
        p.setBedSpawnLocation(dest);
    }

    private void updateCompass() {
        Location l = outlaw.getPlayer().getLocation();
        World w = l.getWorld();
        for(Hunter h : hunters) {
            Player p = h.getPlayer();
            if (p.getWorld().equals(w))
                p.setCompassTarget(l);
            else if (p.getWorld().getEnvironment().equals(World.Environment.NORMAL))
                p.setCompassTarget(outlaw.getLastWorldPos());
            else if (p.getWorld().getEnvironment().equals(World.Environment.NETHER))
                p.setCompassTarget(outlaw.getLastNetherPos());
        }
    }

    private void alertOutlaw() {
        LivingEntity outlawPlayer = outlaw.getRepresentative();
        Location outlawLocation = outlawPlayer.getLocation();
        for (Hunter h : hunters) {
            if (!h.getPlayer().getWorld().equals(outlawLocation.getWorld()))
                continue;
            if (Util.distance(h.getPlayer().getLocation(), outlawLocation) < Cfg.alertRadius) {
                alertTimeoutTimer = Cfg.alertTimeout;
                outlawPlayer.sendMessage("§cHunters near");
                //glow placeholder entity if Outlaw player is offline
                if (!(outlawPlayer instanceof Player)) {
                    outlawPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Cfg.alertTimeout*20, 3));
                }
                break;
            }
        }
    }

    private void alertHunter() {
        Location l = outlaw.getPlayer().getLocation();
        if (isOutside(l)) {
            hunterAlert = true;
            Bukkit.broadcastMessage("§cVictim is breaking through the Wall");
        }
    }

    private void checkEscape() {
        Location l = outlaw.getPlayer().getLocation();
        if ((Math.abs(l.getX()) > escapeArea) || (Math.abs(l.getZ()) > escapeArea)) {
            Bukkit.broadcastMessage("§eVictim escaped!");
            changeGameState(GameState.IDLE);
        }
    }

    public boolean isOutside(Location l) {
        return ((Math.abs(l.getX()) > halfSize+1) || (Math.abs(l.getZ()) > halfSize+1) || l.getY() > 255);
    }

    private void applyPotionHandicap(LivingEntity selectedPlayer) {
        int duration = Bukkit.getOnlinePlayers().size() * 400;
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, duration, 1));
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, duration, 1));
        selectedPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, duration, 1));
    }

        //todo refactor wall methods from Engine

    //todo
    //  refactor code
    //  more stats
    //  late join feature
    //  command alias: manhunt
    //  custom advancements for breaking wall and golden pickaxe

    //todo: re-organize gamemodes impl
}
