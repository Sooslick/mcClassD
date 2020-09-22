package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
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
    private int gameTimer;
    private int killCounter;
    private int alertTimeoutTimer;
    private GameState state;
    private CommandListener cmdListener;
    private Logger log;

    private Runnable votestartTimerImpl = () -> {
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
        cmdListener = new CommandListener(this);
        getCommand("outlaw").setExecutor(cmdListener);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        log.info("Init Class D Plugin - success");
    }

    public void voteStart(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage("§cCannot votestart while game is started");
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
            p.sendMessage("§cCannot suggest while game is started");
            return;
        }
        String name = p.getName();
        if (volunteers.contains(name)) {
            p.sendMessage("§cCannot suggest twice");
            return;
        }
        volunteers.add(name);
        Bukkit.broadcastMessage("§e" + name + " suggest yourself as Victim");
    }

    public GameState getGameState() {
        return state;
    }

    public Outlaw getOutlaw() {
        return outlaw;
    }

    public void sendDebugInfo(CommandSender sender) {
        sender.sendMessage("Current game state: " + state.toString());
        switch (state) {
            case IDLE:
            case PRESTART:
                StringBuilder sb = new StringBuilder("Votestarters: ");
                for(String s : votestarters)
                    sb.append(s).append(" ");
                sender.sendMessage(sb.toString());
                sb = new StringBuilder("Suggesters: ");
                for(String s : volunteers)
                    sb.append(s).append(" ");
                sender.sendMessage(sb.toString());
                sender.sendMessage("Prestart timer: " + votestartCountdown);
                sender.sendMessage("Start zone: " + Cfg.spawnRadius);
                sender.sendMessage("Spawn distance: " + Cfg.spawnDistance);
                break;
            case GAME:
                sender.sendMessage("Victim: " + outlaw.getName());
                sb = new StringBuilder("Hunters: ");
                for(Hunter h : hunters)
                    sb.append(h.getName()).append(" ");
                sender.sendMessage(sb.toString());
                sender.sendMessage("Game time: " + gameTimer);
                sender.sendMessage("Kill counter: " + killCounter);
                sender.sendMessage("Alert timeout: " + alertTimeoutTimer);
                sender.sendMessage("Alert radius: " + Cfg.alertRadius);
                break;
        }
    }

    protected void changeGameState(GameState state) {
        this.state = state;
        log.info("Outlaw game state changed. New state: " + state.toString());
        switch (state) {
            case IDLE:
                Bukkit.getScheduler().cancelTask(gameTimerId);
                Cfg.readConfig(getConfig());
                votestarters = new ArrayList<>();
                volunteers = new ArrayList<>();
                hunters = new ArrayList<>();
                votestartCountdown = Cfg.votestartTimer;
                alertTimeoutTimer = 0;
                gameTimer = 0;
                killCounter = 0;
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke @a everything");
                Bukkit.broadcastMessage("§eReady to next game");
                break;
            case PRESTART:
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage(Cfg.votestartTimer + " seconds to launch");
                break;
            case GAME:
                Bukkit.getScheduler().cancelTask(votestartTimerId);
                Player selectedPlayer;
                Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

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
        p.setTotalExperience(0);
        p.getInventory().clear();       //todo check armor exploit
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
        Player outlawPlayer = outlaw.getPlayer();
        Location outlawLocation = outlawPlayer.getLocation();
        for (Hunter h : hunters) {
            if (Util.distance(h.getPlayer().getLocation(), outlawLocation) < Cfg.alertRadius) {
                alertTimeoutTimer = Cfg.alertTimeout;
                outlawPlayer.sendMessage("§cHunters near");
                break;
            }
        }
    }

    //todo
    //  join / dc events
    //  test + feedback
    //  refactor code
    //  more commands + stats
    //  impl escape gamemode

    //todo Desmond feature: use netherite for spots instead obsidian
}
