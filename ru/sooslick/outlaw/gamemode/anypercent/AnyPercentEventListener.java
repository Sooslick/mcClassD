package ru.sooslick.outlaw.gamemode.anypercent;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.GameState;

public class AnyPercentEventListener implements Listener {

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Engine engine = Engine.getInstance();
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
                engine.triggerEndgame(true);
            }
        }
    }
}
