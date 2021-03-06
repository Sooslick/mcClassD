package ru.sooslick.outlaw.gamemode.anypercent;

import org.bukkit.Bukkit;
import org.bukkit.WorldBorder;
import org.bukkit.event.HandlerList;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeBase;

public class AnyPercentBase implements GameModeBase {
    final AnyPercentEventListener events;

    public AnyPercentBase() {
        events = new AnyPercentEventListener();
        Engine engine = Engine.getInstance();
        engine.getServer().getPluginManager().registerEvents(events, engine);
    }

    @Override
    public void onIdle() {
        events.init();
        int spawnArea = Cfg.spawnRadius + Cfg.spawnDistance;
        int wbSize = spawnArea > 2750 ? spawnArea*2 + 10 : 5500;    //radius of first stronghold's ring is 2688 blocks
        Bukkit.getWorlds().forEach(w -> {
            WorldBorder wb = w.getWorldBorder();
            wb.setCenter(0, 0);
            wb.setSize(wbSize);
        });
        Bukkit.broadcastMessage(Messages.READY_FOR_GAME);
    }

    @Override
    public void onPreStart() {}

    @Override
    public void onGame() {}

    @Override
    public void tick() {}

    @Override
    public void unload() {
        HandlerList.unregisterAll(events);
    }

    @Override
    public String getObjective() {
        return Messages.ANYP_OBJECTIVE;
    }

    @Override
    public String getName() {
        return Messages.ANYP_NAME;
    }

    @Override
    public String getDescription() {
        return Messages.ANYP_DESCRIPTION;
    }
}
