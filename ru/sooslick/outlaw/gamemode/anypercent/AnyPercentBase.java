package ru.sooslick.outlaw.gamemode.anypercent;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.gamemode.GameModeBase;
import ru.sooslick.outlaw.gamemode.GameModeConfig;

public class AnyPercentBase implements GameModeBase {
    AnyPercentEventListener events;

    public AnyPercentBase() {
        events = new AnyPercentEventListener();
        Engine engine = Engine.getInstance();
        engine.getServer().getPluginManager().registerEvents(events, engine);
    }

    @Override
    public void onIdle() {
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
    public GameModeConfig getConfig() {
        return null;
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
