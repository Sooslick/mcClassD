package ru.sooslick.outlaw.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.sooslick.outlaw.Messages;

import java.time.Duration;
import java.util.Collection;
import java.util.Random;

public class CommonUtil {
    public static Random random = new Random();

    public static final String DEATH_MESSAGE_BASE = "§4%s %s %s";    //who, reason, by
    private static final String DURATION_DEFAULT = "%d:%02d";
    private static final String DURATION_HOURS = "%d:%02d:%02d";
    private static final String PLACEHOLDER = "";

    private CommonUtil() {}

    public static <E> E getRandomOf(Collection<E> set) {
        return set.stream().skip(random.nextInt(set.size())).findFirst().orElse(null);
    }

    public static String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long h = seconds / 3600;
        if (h > 0) {
            return String.format(
                    DURATION_HOURS,
                    seconds / 3600,
                    (seconds % 3600) / 60,
                    seconds % 60);
        } else {
            return String.format(
                    DURATION_DEFAULT,
                    (seconds % 3600) / 60,
                    seconds % 60);
        }
    }

    public static String getDeathMessage(Player dead, Entity killer, EntityDamageEvent.DamageCause cause) {
        String reason;
        switch (cause) {
            case ENTITY_ATTACK: reason = Messages.DEATH_BY_ATTACK; break;
            case ENTITY_EXPLOSION: reason = Messages.DEATH_BY_EXPLOSION; break;
            case FALL: reason = Messages.DEATH_BY_FALL; break;
            case FIRE:
            case FIRE_TICK:
            case LAVA: reason = Messages.DEATH_BY_FIRE; break;
            case PROJECTILE: reason = Messages.DEATH_BY_PROJECTILE; break;
            default: reason = Messages.DEATH_BY_DEFAULT;
        }
        String by = killer == null ? PLACEHOLDER : Messages.BY + killer.getName();
        return String.format(DEATH_MESSAGE_BASE, dead.getName(), reason, by);
    }
}
