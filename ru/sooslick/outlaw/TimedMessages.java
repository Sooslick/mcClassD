package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sooslick.outlaw.roles.Outlaw;
import ru.sooslick.outlaw.util.CommonUtil;

import java.time.Duration;

public class TimedMessages {

    private int delay;
    private int timer;
    private int timerTaskId;

    private Runnable timerImpl = () -> {
        if (timer-- <= 0)
            broadcast();
    };

    public TimedMessages() {
        reset();
    }

    public String getMessage() {
        Engine engine = Engine.getInstance();
        switch (engine.getGameState()) {
            case IDLE:
            case PRESTART:
                //todo: gamemode impl
                return String.format(Messages.TIMED_MESSAGE_RULES, Cfg.enableEscapeGamemode ? "The Wall" : "Minecraft Any%");
            case GAME:
                Duration duration = Duration.ofSeconds(engine.getGameTimer());
                Outlaw o = engine.getOutlaw();
                String outlawString = o.getEntity() instanceof Player ? Messages.VICTIM : Messages.VICTIM_CHICKEN;
                return String.format(Messages.TIMED_MESSAGE_STATS,
                        CommonUtil.formatDuration(duration),        //Time elapsed
                        engine.getKillCounter(),                    //Death counter
                        outlawString, o.getName());                 //Victim Entity + name
        }
        return null;
    }

    public TimedMessages launch() {
        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(Engine.getInstance(), timerImpl, 1, 20);
        return this;
    }

    public void destroy() {
        Bukkit.getScheduler().cancelTask(timerTaskId);
    }

    private void broadcast() {
        Bukkit.broadcastMessage(getMessage());
        resetTimer();
    }

    private void reset() {
        delay = 300;            //todo cfg
        resetTimer();
    }

    private void resetTimer() {
        timer = delay;
    }
}
