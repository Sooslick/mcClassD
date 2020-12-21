package ru.sooslick.outlaw.util;

import java.time.Duration;
import java.util.Collection;
import java.util.Random;

public class CommonUtil {
    public static Random random = new Random();

    private static final String DURATION_DEFAULT = "%d:%02d";
    private static final String DURATION_HOURS = "%d:%02d:%02d";

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
}
