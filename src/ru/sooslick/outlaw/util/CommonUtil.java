package ru.sooslick.outlaw.util;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import ru.sooslick.outlaw.Messages;

import java.time.Duration;
import java.util.Collection;
import java.util.Random;

/**
 * Utility class with common methods like formatting or random
 */
public class CommonUtil {
    public static final Random random = new Random();

    public static final String DEATH_MESSAGE_BASE = "§4%s %s %s";    //who, reason, by
    private static final String DURATION_DEFAULT = "%d:%02d";
    private static final String DURATION_HOURS = "%d:%02d:%02d";
    private static final String PLACEHOLDER = "";

    private CommonUtil() {}

    /**
     * Returns one random element from collection
     * @param set collection
     * @return random element of collection
     */
    public static <E> E getRandomOf(Collection<E> set) {
        if (set.size() <= 0)
            return null;
        return set.stream().skip(random.nextInt(set.size())).findFirst().orElse(null);
    }

    /**
     * Format duration in H:MM:SS
     * @param secondsTotal amount of seconds
     * @return formatted duration
     */
    public static String formatDuration(long secondsTotal) {
        Duration duration = Duration.ofSeconds(secondsTotal);
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

    /**
     * Format custom death message for damage event
     * @param dead who is dead
     * @param killer who is killer
     * @param cause type of fatal damage
     * @return formatted death message
     */
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
            case DROWNING:
            case SUFFOCATION: reason = Messages.DEATH_BY_SUFFOCATION; break;
            default: reason = Messages.DEATH_BY_DEFAULT;
        }
        String by = killer == null ? PLACEHOLDER : Messages.BY + killer.getName();
        return String.format(DEATH_MESSAGE_BASE, dead.getName(), reason, by);
    }
}
