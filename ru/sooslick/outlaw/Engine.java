package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    private int minVotestarters;
    private int votestartTimer;
    private int votestartTimerId;
    private int gameTimerId;
    private int spawnDistance;
    private int spawnRadius;
    private int gameTimer;
    private int killCounter;
    private int alertTimeout;
    private int alertThreshold;
    private GameState state;
    private CommandListener cmdListener;
    private Logger log;

    private Runnable votestartTimerImpl = () -> {
        votestartTimer--;
        if (votestartTimer % 10 == 0) {
            Bukkit.broadcastMessage(votestartTimer + " seconds to start");
        }
        if (votestartTimer <= 0) {
            changeGameState(GameState.GAME);
        }
    };

    private Runnable gameProcessor = () -> {
        gameTimer++;
        if (alertTimeout > 0)
            alertTimeout--;
        else
            alertOutlaw();
        updateCompass();
    };

    @Override
    public void onEnable() {
        log = Bukkit.getLogger();
        log.info("Init Class D Plugin");
        changeGameState(GameState.IDLE);
        cmdListener = new CommandListener(this);
        getCommand("outlaw").setExecutor(cmdListener);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        log.info("Init Class D Plugin - success");
    }

    public void voteStart(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage("Cannot votestart while game is started");
            return;
        }
        String name = p.getName();
        if (votestarters.contains(name)) {
            p.sendMessage("Cannot votestart twice");
            return;
        }
        votestarters.add(name);
        if (votestarters.size() >= minVotestarters && state == GameState.IDLE) {
            changeGameState(GameState.PRESTART);
            return;
        }
    }

    public void suggest(Player p) {
        if (state == GameState.GAME) {
            p.sendMessage("Cannot suggest while game is started");
            return;
        }
        String name = p.getName();
        if (volunteers.contains(name)) {
            p.sendMessage("Cannot suggest twice");
            return;
        }
        volunteers.add(name);
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
                sender.sendMessage("Prestart timer: " + votestartTimer);
                sender.sendMessage("Start zone: " + spawnRadius);
                sender.sendMessage("Spawn distance: " + spawnDistance);
                break;
            case GAME:
                sender.sendMessage("Outlaw: " + outlaw.getName());
                sb = new StringBuilder("Hunters: ");
                for(Hunter h : hunters)
                    sb.append(h.getName()).append(" ");
                sender.sendMessage("Hunters: " + sb.toString());
                sender.sendMessage("Game time: " + gameTimer);
                sender.sendMessage("Kill counter: " + killCounter);
                sender.sendMessage("Alert timeout: " + alertTimeout);
                sender.sendMessage("Alert threshold: " + alertThreshold);
                break;
        }
    }

    protected void changeGameState(GameState state) {
        this.state = state;
        log.info("Outlaw game state changed. New state: " + state.toString());
        switch (state) {
            case IDLE:
                Bukkit.getScheduler().cancelTask(gameTimerId);      //todo if timer !exists???
                votestarters = new ArrayList<>();
                volunteers = new ArrayList<>();
                hunters = new ArrayList<>();
                votestartTimer = 60;        //todo get values from cfg
                minVotestarters = 1;        //cfg
                spawnDistance = 228;        //cfg
                spawnRadius = 228;          //cfg
                alertThreshold = 66;        //cfg + todo: default thresold timer
                alertTimeout = 0;
                gameTimer = 0;
                killCounter = 0;
                for (Player p : Bukkit.getOnlinePlayers())
                    p.setGameMode(GameMode.SPECTATOR);
                Bukkit.broadcastMessage("ready to next game");
                break;
            case PRESTART:
                votestartTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, votestartTimerImpl, 1, 20);
                Bukkit.broadcastMessage("Game launch soon");
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
                Bukkit.broadcastMessage("Outlaw: " + selectedPlayer.getName());

                //process outlaw
                outlaw = new Outlaw(selectedPlayer);
                Location outlawLocation = Util.getSafeRandomLocation(spawnRadius);
                preparePlayer(selectedPlayer, outlawLocation);

                //process others
                Location hunterLocation = Util.getSafeDistanceLocation(outlawLocation, spawnDistance);
                for (Player p : onlinePlayers) {
                    //skip outlaw
                    if (p.equals(selectedPlayer))
                        continue;
                    //add others to hunter team
                    Hunter h = new Hunter(p);
                    hunters.add(h);
                    preparePlayer(p, hunterLocation);
                }

                //run game
                gameTimerId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, gameProcessor, 1, 20);
                Bukkit.broadcastMessage("Game started. Run!");
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
        for(Hunter h : hunters) {
            h.getPlayer().setCompassTarget(l);
        }
    }

    private void alertOutlaw() {
        Player outlawPlayer = outlaw.getPlayer();
        Location outlawLocation = outlawPlayer.getLocation();
        for(Hunter h : hunters) {
            Location l = h.getPlayer().getLocation();
            int x = l.getBlockX() - outlawLocation.getBlockX();
            int z = l.getBlockZ() - outlawLocation.getBlockZ();
            if (Math.sqrt(x*x + z*z) < alertThreshold) {
                alertTimeout = 60;                                  //todo magic const to cfg
                outlawPlayer.sendMessage("Hunters near");
                break;
            }
        }
    }

    //todo
    //  join / dc events
    //  test + feedback
    //  refactor code
    //  cfg impl
    //  more commands + stats
    //  impl escape gamemode
}
