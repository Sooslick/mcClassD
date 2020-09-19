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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.roles.Outlaw;

public class EventListener implements Listener {

    private Engine engine;

    public EventListener(Engine engine) {
        this.engine = engine;
    }

    @EventHandler
    public void OnDamage(EntityDamageEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getDamage() <= 0) {
                Bukkit.broadcastMessage("§cDragon died. §eVictim escaped!");
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
            Bukkit.broadcastMessage("§cVictim died. §eHunters win!");
            engine.changeGameState(GameState.IDLE);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.getPlayer().getInventory().addItem(new ItemStack(Material.COMPASS));
        //todo adequate fix
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
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
}
