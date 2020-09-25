package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.roles.Outlaw;

public class EventListener implements Listener {

    private Engine engine;

    private boolean firstBlockAlert;

    public EventListener(Engine engine) {
        this.engine = engine;
        reset();
    }

    @EventHandler
    public void OnDamage(EntityDamageEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getDamage() <= 0) {
                Bukkit.broadcastMessage("§cDragon died. §eVictim escaped!");            //todo: impl method victory in Engine
                engine.changeGameState(GameState.IDLE);
            }
            return;
        }

        //check if outlaw dead
        if (!(e.getEntity() instanceof Player))
            return;
        Player p = (Player) e.getEntity();
        if (!(engine.getOutlaw().getPlayer().equals(p)))
            return;
        if (p.getHealth() - e.getDamage() <= 0) {
            Bukkit.broadcastMessage("§cVictim died. §eHunters win!");   //todo: impl method victory in Engine
            engine.changeGameState(GameState.IDLE);
        }

        //todo: check if hunter s ded
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
        //todo test onPortal + onEnderPearl conflicts
        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location l = e.getTo();
            if (engine.isOutside(l))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!firstBlockAlert)
            return;
        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;
        Location l = e.getBlock().getLocation();
        if ((Math.abs(l.getBlockX()) >= Wall.startPosY) || (Math.abs(l.getBlockZ()) >= Wall.startPosY)) {
            firstBlockAlert = true;
            Bukkit.broadcastMessage("§cVictim is trying to break the Wall");       //todo refactor broadcast to broadcaster class
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!Cfg.enableEscapeGamemode)
            return;

        Location l = e.getBlockPlaced().getLocation();
        if (l.getBlockY() < 240)
            return;

        //generate weird barrier for wall gamemode to prevent escape over the wall
        World w = l.getWorld();
        w.getBlockAt(l.getBlockX(), 255, l.getBlockZ()).setType(Material.BARRIER);
        w.getBlockAt(l.getBlockX()-1, 255, l.getBlockZ()).setType(Material.BARRIER);
        w.getBlockAt(l.getBlockX(), 255, l.getBlockZ()-1).setType(Material.BARRIER);
        w.getBlockAt(l.getBlockX()+1, 255, l.getBlockZ()).setType(Material.BARRIER);
        w.getBlockAt(l.getBlockX(), 255, l.getBlockZ()+1).setType(Material.BARRIER);
    }

    public void reset() {
        firstBlockAlert = false;
    }
}
