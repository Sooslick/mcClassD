package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;
import ru.sooslick.outlaw.util.CommonUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class EventListener implements Listener {

    private boolean firstBlockAlerted;
    private boolean goldenPickaxeAlerted;

    public EventListener() {
        reset();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        Entity eventEntity = e.getEntity();
        if (eventEntity instanceof Player) {
            Player eventPlayer = (Player) eventEntity;
            if (eventPlayer.getHealth() - e.getFinalDamage() <= 0) {
                Entity damager = e instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) e).getDamager() : null;
                Bukkit.broadcastMessage(CommonUtil.getDeathMessage(eventPlayer, damager, e.getCause()));
            }
        }

        //check if dragon dead
        //todo: GAMEMODE IMPL
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
                engine.triggerEndgame(true);
            }
            return;
        }

        //check if outlaw dead
        LivingEntity outlaw = engine.getOutlaw().getEntity();
        if (eventEntity.equals(outlaw)) {
            if (outlaw.getHealth() - e.getFinalDamage() <= 0) {
                e.setCancelled(true);
                engine.triggerEndgame(false);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.setDeathMessage(null);
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;
        engine.incKill();
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
        if (Cfg.enableEscapeGamemode) {
            e.setCancelled(true);
            return;
        }
        Outlaw o = Engine.getInstance().getOutlaw();
        if (!e.getPlayer().equals(o.getPlayer()))
            return;
        o.setTrackedLocation(e.getFrom());
    }

    @EventHandler
    public void onEnderPearl(PlayerTeleportEvent e) {
        if (!Cfg.enableEscapeGamemode) {
            return;
        }
        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location l = e.getTo();
            if (Engine.getInstance().isOutside(l))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonMove(BlockPistonExtendEvent e) {
        Engine engine = Engine.getInstance();
        ChestTracker ct = engine.getChestTracker();
        List<Block> movedBlocks = new LinkedList<>();
        for (Block b : e.getBlocks()) {
            Block moved = b.getRelative(e.getDirection(), 1);
            ct.detectBlock(moved, true);
            movedBlocks.add(moved);
        }
        //todo gamemode
        if (Cfg.enableEscapeGamemode)
            WorldUtil.generateBarrier(movedBlocks);
    }

    //todo: gamemode listener
    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Engine engine = Engine.getInstance();
        if (!Cfg.enableEscapeGamemode)
            return;
        if (engine.getGameState() != GameState.GAME)
            return;

        //TODO: WALL SCOREBOARD
        engine.getScoreboardHolder().recalculateScore(e.getBlock().getLocation());

        if (firstBlockAlerted)
            return;

        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;
        Location l = e.getBlock().getLocation();
        int halfsize = engine.getHalfSize();
        if ((Math.abs(l.getBlockX()) >= halfsize) || (Math.abs(l.getBlockZ()) >= halfsize)) {
            firstBlockAlerted = true;
            Bukkit.broadcastMessage("§cVictim is trying to break the Wall");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        Engine engine = Engine.getInstance();
        //detect beds and chests
        Block b = e.getBlockPlaced();
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            ct.detectBlock(b);

        if (!Cfg.enableEscapeGamemode)
            return;

        //detect wall restoring
        Material m = b.getType();
        if (m == Material.OBSIDIAN || m == Material.NETHERITE_BLOCK || m == Material.CRYING_OBSIDIAN || m == Material.ANCIENT_DEBRIS) {
            int halfsize = engine.getHalfSize();
            if ((Math.abs(b.getX()) >= halfsize - 1) || (Math.abs(b.getZ()) >= halfsize - 1)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§4Obsidian is restricted here");
            }
        }

        //detect towering escape attempts
        WorldUtil.generateBarrier(Collections.singletonList(b));
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
            //todo gamemode impl
            p.sendMessage(String.format(Messages.ABOUT, Cfg.enableEscapeGamemode ? "The Wall" : "Minecraft Any%"));
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
        for (Hunter h : engine.getHunters()) {
            if (h.getPlayer().getName().equals(p.getName())) {
                h.setPlayer(p);
                p.sendMessage(Messages.HUNTER_REMINDER);
                e.setJoinMessage(String.format(Messages.HUNTER_JOINED, p.getName()));
                return;
            }
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
        //todo move spawn and assignments to goOffline
        if (o.getPlayer().equals(p)) {
            LivingEntity entity = (LivingEntity) p.getWorld().spawnEntity(p.getLocation(), EntityType.CHICKEN);
            entity.setAI(false);
            entity.setCustomName(p.getName());
            o.goOffline(entity);
            e.setQuitMessage(null);
        }
    }

    //todo: move to gamemode listener
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;
        if (e.getWhoClicked().equals(Engine.getInstance().getOutlaw().getPlayer()))
            detectGoldPickaxe(e.getCurrentItem());
    }

    //todo: move to gamemode listener
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;
        if (e.getEntity().equals(Engine.getInstance().getOutlaw().getPlayer()))
            detectGoldPickaxe(e.getItem().getItemStack());
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        // Constantly detects entities!
        //todo onEntitySpawn and onVehicleSpawn - same code. Move to method?
        Engine engine = Engine.getInstance();
        //ChestTracker created after changeGameState - Game and its value is null before first game
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            engine.getChestTracker().detectEntity(e.getEntity());
    }

    @EventHandler
    public void onVehicleSpawn(VehicleCreateEvent e) {
        // Constantly detects entities!
        Engine engine = Engine.getInstance();
        //ChestTracker created after changeGameState - Game and its value is null before first game
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            engine.getChestTracker().detectEntity(e.getVehicle());
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

    public void reset() {
        firstBlockAlerted = false;
        goldenPickaxeAlerted = false;
    }

    //todo: Gamemode
    private void detectGoldPickaxe(ItemStack is) {
        if (!Cfg.enableEscapeGamemode)
            return;
        Engine engine = Engine.getInstance();
        if (goldenPickaxeAlerted)
            return;
        if (is.getType() == Material.GOLDEN_PICKAXE) {
            goldenPickaxeAlerted = true;
            Bukkit.broadcastMessage("§cGolden pickaxe detected");
        }
    }
}
