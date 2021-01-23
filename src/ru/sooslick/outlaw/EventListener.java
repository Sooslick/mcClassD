package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;
import ru.sooslick.outlaw.util.CommonUtil;

class EventListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        Entity damagedEntity = e.getEntity();
        double dmg = e.getFinalDamage();
        double statDmg = dmg > 20 ? 20 : dmg;
        if (damagedEntity instanceof Player) {
            // who (almost for stats)
            Player damagedPlayer = (Player) damagedEntity;
            Hunter damagedHunter = engine.getHunter(damagedPlayer);
            LivingEntity outlawEntity = engine.getOutlaw().getEntity();
            //by who (for stats)
            Entity killer = null;
            Hunter killerHunter = null;
            boolean byOutlaw = false;

            //check by who
            if (e instanceof EntityDamageByEntityEvent) {
                killer = ((EntityDamageByEntityEvent) e).getDamager();
                if (killer instanceof Projectile)
                    killer = (Entity) ((Projectile) killer).getShooter();
                if (killer instanceof Player) {
                    killerHunter = engine.getHunter((Player) killer);
                    byOutlaw = killer.equals(outlawEntity);
                }
            }

            // format death message
            if (damagedPlayer.getHealth() - dmg <= 0) {
                Bukkit.broadcastMessage(CommonUtil.getDeathMessage(damagedPlayer, killer, e.getCause()));
            }

            //check if hunter is damaged
            if (damagedHunter != null){
                engine.getStatsCollector().countHunterDamage(damagedHunter, statDmg, byOutlaw);
                return;
            }

            //check if outlaw dead
            if (damagedEntity.equals(outlawEntity)) {
                engine.getStatsCollector().countVictimDamage(killerHunter, statDmg);
                if (outlawEntity.getHealth() - dmg <= 0) {
                    e.setCancelled(true);
                    engine.triggerEndgame(false);
                }
            }
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent ede) {
        Engine engine = Engine.getInstance();
        if (ede instanceof PlayerDeathEvent) {
            PlayerDeathEvent pde = (PlayerDeathEvent) ede;
            pde.setDeathMessage(null);
            if (engine.getGameState() != GameState.GAME)
                return;
            Hunter h = engine.getHunter(pde.getEntity().getPlayer());
            if (h != null)
                engine.getStatsCollector().countDeath(h);
            return;
        }

        //chickens
        if (ede.getEntity() instanceof Chicken)
            engine.getStatsCollector().countChicken();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Hunter h = Engine.getInstance().getHunter(e.getPlayer());
        if (h != null) {
            h.onRespawn();
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        Outlaw o = engine.getOutlaw();
        if (!e.getPlayer().equals(o.getPlayer()))
            return;
        o.setTrackedLocation(e.getFrom());
    }

    @EventHandler
    public void onPistonMove(BlockPistonExtendEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        ChestTracker ct = engine.getChestTracker();
        e.getBlocks().forEach(b -> ct.detectBlock(b.getRelative(e.getDirection(), 1), true));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        //detect beds and chests
        Block b = e.getBlockPlaced();
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            ct.detectBlock(b);
    }

    @EventHandler
    public void onBucket(PlayerBucketEmptyEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            ct.detectBlock(e.getBlock(), true);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK)
            engine.getChestTracker().detectBlock(e.getClickedBlock());

        //check compass
        Player p = e.getPlayer();
        Hunter h = engine.getHunter(p);
        if (h == null)
            return;
        if (e.getMaterial() == Material.COMPASS)
            if (h.updateCompass())
                p.sendMessage(String.format(Messages.COMPASS_UPDATED, engine.getOutlaw().getName()));
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        engine.getChestTracker().detectEntity(e.getRightClicked());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Engine engine = Engine.getInstance();
        Player p = e.getPlayer();
        if (!engine.getGameState().equals(GameState.GAME)) {
            p.setGameMode(GameMode.SPECTATOR);
            p.sendMessage(String.format(Messages.ABOUT, engine.getGameMode().getName()));
            return;
        }
        Outlaw o = engine.getOutlaw();
        //nametag bugfix
        engine.getScoreboardHolder().setPlayerScoreboard(p);

        //check Outlaw
        if (p.getName().equals(o.getPlayer().getName())) {
            o.goOnline(p);
            e.setJoinMessage(null);
            return;
        }

        //check hunter
        Hunter h = engine.getHunter(p);
        if (h != null) {
            h.goOnline(p);
            e.setJoinMessage(String.format(Messages.HUNTER_JOINED, p.getName()));
            return;
        }

        //set spectator mode for anyone else
        p.setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME)) {
            engine.unvote(e.getPlayer());
            return;
        }

        Player p = e.getPlayer();
        Outlaw o = engine.getOutlaw();
        if (o.getPlayer().equals(p)) {
            o.goOffline();
            e.setQuitMessage(null);
            return;
        }

        Hunter h = engine.getHunter(p);
        if (h != null)
            h.goOffline();
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        detectEntity(e.getEntity());
    }

    @EventHandler
    public void onVehicleSpawn(VehicleCreateEvent e) {
        detectEntity(e.getVehicle());
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        if (e.isCancelled())
            return;
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        if (!e.getPlayer().equals(engine.getOutlaw().getPlayer()))
            return;
        if (e.getItem().getType() == Material.MILK_BUCKET) {
            engine.setGlowingRefreshTimer(Cfg.milkGlowImmunityDuration);
        }
    }

    private void detectEntity(Entity e) {
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        engine.getChestTracker().detectEntity(e);
    }
}
