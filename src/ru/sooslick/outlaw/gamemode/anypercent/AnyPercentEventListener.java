package ru.sooslick.outlaw.gamemode.anypercent;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.GameState;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.LinkedList;
import java.util.List;

public class AnyPercentEventListener implements Listener {
    private static final String DEBUG_CHECK_DRAGON = "Checking Dragon state";
    private static final String DEBUG_CRYSTAL_CLEANUP = "Removed %d crystals";
    private static final String DEBUG_CRYSTAL_RECREATED = "Recreated %d crystals";
    private static final String DEBUG_CRYSTAL_SAVED = "Saved crystal position for rollback. %s";
    private static final String DEBUG_CRYSTAL_TRACKED = "Tracked created crystal at %s";
    private static final String DEBUG_DRAGON_DETECTED = "Detected living dragon. Rolling back crystals";
    private static final String DEBUG_DRAGON_SUMMONED = "No dragon found. Summoning new dragon";

    private boolean dragonChecked = false;
    private List<Entity> createdCrystals = new LinkedList<>();
    private List<Entity> rollbackCrystals = new LinkedList<>();
    private DragonBattle savedDragonBattle = null;

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        Entity entity = e.getEntity();
        if (entity.getType() == EntityType.ENDER_DRAGON) {
            if (((LivingEntity) entity).getHealth() - e.getFinalDamage() <= 0) {
                engine.triggerEndgame(true);
            }
        }

        if (entity.getType() == EntityType.ENDER_CRYSTAL) {
            if (savedDragonBattle == null || savedDragonBattle.getRespawnPhase() == DragonBattle.RespawnPhase.SUMMONING_PILLARS)
                return;
            for (Entity cc : createdCrystals)
                if (entity.equals(cc))
                    return;
            rollbackCrystals.add(entity);
            LoggerUtil.debug(String.format(DEBUG_CRYSTAL_SAVED, WorldUtil.formatLocation(entity.getLocation())));
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME || e.getTo() == null || dragonChecked)
            return;

        //check world;
        World dest = e.getTo().getWorld();
        if (dest == null || dest.getEnvironment() != World.Environment.THE_END)
            return;

        dragonChecked = true;
        new BukkitRunnable() {
            @Override
            public void run() {
                LoggerUtil.info(DEBUG_CHECK_DRAGON);
                savedDragonBattle = dest.getEnderDragonBattle();
                if (savedDragonBattle == null) {
                    Bukkit.broadcastMessage(Messages.ANYP_SUMMON_ALERT);
                    return;
                }
                if (savedDragonBattle.getEnderDragon() == null) {
                    int y = dest.getHighestBlockYAt(0, 3) + 1;
                    LoggerUtil.info(DEBUG_DRAGON_SUMMONED);
                    dest.spawnEntity(new Location(dest, 3.5, y, 0.5), EntityType.ENDER_CRYSTAL).setInvulnerable(true);
                    dest.spawnEntity(new Location(dest, -2.5, y, 0.5), EntityType.ENDER_CRYSTAL).setInvulnerable(true);
                    dest.spawnEntity(new Location(dest, 0.5, y, 3.5), EntityType.ENDER_CRYSTAL).setInvulnerable(true);
                    dest.spawnEntity(new Location(dest, 0.5, y, -2.5), EntityType.ENDER_CRYSTAL).setInvulnerable(true);
                    savedDragonBattle.initiateRespawn();
                } else {
                    LoggerUtil.info(DEBUG_DRAGON_DETECTED);
                    savedDragonBattle.resetCrystals();
                }
            }
        }.runTaskLater(engine, 20);
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        Entity ent = e.getEntity();
        if (ent.getType() == EntityType.ENDER_CRYSTAL) {
            if (savedDragonBattle == null || savedDragonBattle.getRespawnPhase() == DragonBattle.RespawnPhase.SUMMONING_PILLARS)
                return;
            createdCrystals.add(ent);
            LoggerUtil.debug(String.format(DEBUG_CRYSTAL_TRACKED, WorldUtil.formatLocation(ent.getLocation())));
        }
    }

    public void init() {
        //rollback
        createdCrystals.forEach(Entity::remove);
        LoggerUtil.debug(String.format(DEBUG_CRYSTAL_CLEANUP, createdCrystals.size()));
        if (savedDragonBattle != null) {
            if (savedDragonBattle.getEnderDragon() != null) {
                rollbackCrystals.forEach(entity -> entity.getWorld().spawnEntity(entity.getLocation(), EntityType.ENDER_CRYSTAL));
                LoggerUtil.debug(String.format(DEBUG_CRYSTAL_RECREATED, rollbackCrystals.size()));
            }
        }
        //init
        dragonChecked = false;
        createdCrystals = new LinkedList<>();
        rollbackCrystals = new LinkedList<>();
    }
}
