package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.sooslick.outlaw.roles.Outlaw;

import java.time.Duration;

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
                StringBuilder sb = new StringBuilder();
                sb.append("§e`Class D` Manhunt gamemode")
                        .append("\nType §6/manhunt help §efor more info")
                        .append("\nTry yourself: §6/manhunt suggest")
                        .append("\n§eType §6/manhunt votestart §eor simply §6/mh v §eto begin")
                        .append("\n§ePreferred gamemode: §c")
                        .append(Cfg.enableEscapeGamemode ? "The Wall" : "Minecraft Any%");
                return sb.toString();
            case GAME:
                Duration duration = Duration.ofSeconds(engine.getGameTimer());
                Outlaw o = engine.getOutlaw();
                String outlawString = o.getEntity() instanceof Player ? "Victim" : "Victim Chicken";
                sb = new StringBuilder();
                sb.append("§eGame timer: ").append(Util.formatDuration(duration))
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
