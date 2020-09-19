package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

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
                Bukkit.broadcastMessage("Dragon died. Outlaw win!");
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
            Bukkit.broadcastMessage("Outlaw died. Hunters win!");
            engine.changeGameState(GameState.IDLE);
        }
    }

}
