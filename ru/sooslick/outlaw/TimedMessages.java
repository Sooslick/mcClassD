package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sooslick.outlaw.roles.Outlaw;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimedMessages {

    private Engine engine;
    private int delay;
    private int timer;
    private int timerTaskId;

    private Runnable timerImpl = () -> {
        if (timer-- <= 0)
            broadcast();
    };

    public TimedMessages(Engine e) {
        engine = e;
        reset();
    }

    public String getMessage() {
        switch (engine.getGameState()) {
            case IDLE:
            case PRESTART:
                //todo impl
                return "TimedMessages.getMessage() not implemented for idle/prestart states";
            case GAME:
                Date date = new Date(engine.getGameTimer() * 1000);
                Outlaw o = engine.getOutlaw();
                String outlawString = o.getRepresentative() instanceof Player ? "Victim" : "Victim Chicken";
                StringBuilder sb = new StringBuilder();
                sb.append("§eGame timer: ").append(new SimpleDateFormat("HH:mm:ss").format(date))
                        .append("\nDeath counter: ").append(engine.getKillCounter())
                        .append("\nCompass is pointing to §c").append(outlawString).append(" §o").append(o.getName());
                return sb.toString();
        }
        return "§4Error: TimedMessages.getMessage() returned nothing";
    }

    public TimedMessages launch() {
        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(engine, timerImpl, 1, 20);
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
