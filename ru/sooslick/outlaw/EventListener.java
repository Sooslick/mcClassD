package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;

public class EventListener implements Listener {

    private Engine engine;
    private boolean firstBlockAlerted;

    public EventListener(Engine engine) {
        this.engine = engine;
        reset();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
                Bukkit.broadcastMessage("§cDragon died. §eVictim escaped!");            //todo: impl method victory in Engine
                engine.changeGameState(GameState.IDLE);
            }
            return;
        }

        //check if outlaw dead
        LivingEntity outlaw = engine.getOutlaw().getRepresentative();
        if (!e.getEntity().equals(outlaw))
            return;
        if (outlaw.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            //todo inv to chest method?
            Bukkit.broadcastMessage("§cVictim died. §eHunters win!");   //todo: impl method victory in Engine
            engine.changeGameState(GameState.IDLE);
        }

        //todo: check if hunter s ded
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check outlaw
        Entity eventEntity = e.getEntity();
        LivingEntity outlaw = engine.getOutlaw().getRepresentative();
        if (e.getEntity().equals(outlaw))
            return;

        //else inc killcounter
        if (eventEntity instanceof Player)
            engine.incKill();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.getPlayer().getInventory().addItem(new ItemStack(Material.COMPASS));
        //todo adequate fix
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (Cfg.enableEscapeGamemode) {
            e.setCancelled(true);
            return;
        }
        Outlaw o = engine.getOutlaw();
        if (!e.getPlayer().equals(o.getPlayer()))
            return;
        Location from = e.getFrom();
        World.Environment env = from.getWorld().getEnvironment();
        if (env.equals(World.Environment.NORMAL))
            o.setLastWorldPos(from);
        else if (env.equals(World.Environment.NETHER))
            o.setLastNetherPos(from);
    }

    @EventHandler
    public void onEnderPearl(PlayerTeleportEvent e) {
        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location l = e.getTo();
            if (engine.isOutside(l))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!Cfg.enableEscapeGamemode)
            return;
        if (!engine.getGameState().equals(GameState.GAME))
            return;
        if (firstBlockAlerted)
            return;

        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;
        Location l = e.getBlock().getLocation();
        if ((Math.abs(l.getBlockX()) >= Wall.startPosY) || (Math.abs(l.getBlockZ()) >= Wall.startPosY)) {
            firstBlockAlerted = true;
            Bukkit.broadcastMessage("§cVictim is trying to break the Wall");       //todo refactor broadcast to broadcaster class
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!Cfg.enableEscapeGamemode)
            return;

        Block b = e.getBlockPlaced();
        Location l = b.getLocation();
        if (!Cfg.allowBuildWall) {
            Material m = b.getType();
            if (m.equals(Material.OBSIDIAN) || m.equals(Material.NETHERITE_BLOCK) || m.equals(Material.CRYING_OBSIDIAN)) {
                if ((Math.abs(l.getBlockX()) >= Wall.startPosY - 1) || (Math.abs(l.getBlockZ()) >= Wall.startPosY - 1)) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage("Obsidian is denied here");
                }
            }
        }

        if (l.getBlockY() > 240) {
            //generate weird barrier for wall gamemode to prevent escape over the wall
            World w = l.getWorld();
            w.getBlockAt(l.getBlockX(), 255, l.getBlockZ()).setType(Material.BARRIER);
            w.getBlockAt(l.getBlockX() - 1, 255, l.getBlockZ()).setType(Material.BARRIER);
            w.getBlockAt(l.getBlockX(), 255, l.getBlockZ() - 1).setType(Material.BARRIER);
            w.getBlockAt(l.getBlockX() + 1, 255, l.getBlockZ()).setType(Material.BARRIER);
            w.getBlockAt(l.getBlockX(), 255, l.getBlockZ() + 1).setType(Material.BARRIER);
            //todo: piston exploit
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!engine.getGameState().equals(GameState.GAME)) {
            //todo infomessages
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            return;
        }
        Player p = e.getPlayer();
        Outlaw o = engine.getOutlaw();

        //check Outlaw
        if (p.getName().equals(o.getPlayer().getName())) {
            o.goOnline(p);
            return;
        }

        //check hunter
        for (Hunter h : engine.getHunters()) {
            if (h.getPlayer().getName().equals(p.getName())) {
                h.setPlayer(p);
                return;
            }
        }

        //set spectator mode for anyone else
        p.setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            //todo: remove from vs / suggest
            return;
        Player p = e.getPlayer();
        Outlaw o = engine.getOutlaw();
        if (o.getPlayer().equals(p)) {
            LivingEntity entity = (LivingEntity) p.getWorld().spawnEntity(p.getLocation(), EntityType.CHICKEN);
            entity.setAI(false);
            entity.setCustomName(p.getName());
            o.goOffline(entity);
        }
    }

    public void reset() {
        firstBlockAlerted = false;
    }
}
