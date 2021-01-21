package ru.sooslick.outlaw.util;

import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;

import java.util.logging.Logger;

public class LoggerUtil {

    private static final String PREFIX = "[DEBUG] ";
    private static final Logger LOG = Engine.getInstance().getLogger();
    private static final DebugLogger DEBUG_NORMAL = LOG::fine;
    private static final DebugLogger DEBUG_FORCED = (msg) -> LOG.info(PREFIX + msg);

    private static DebugLogger logFunc = DEBUG_NORMAL;

    //disable constructor for Utility class
    private LoggerUtil() {}

    public static void setupLevel() {
        logFunc = Cfg.debugMode ? DEBUG_FORCED : DEBUG_NORMAL;
    }

    //just logs message at fine level in normal mode.
    //For plugin's debug mode forces debug messages at info level
    public static void debug(String msg) {
        logFunc.debug(msg);
    }

    public static void info(String msg) {
        LOG.info(msg);
    }

    public static void warn(String msg) {
        LOG.warning(msg);
    }

    @FunctionalInterface
    private interface DebugLogger {
        void debug(String msg);
    }
}
