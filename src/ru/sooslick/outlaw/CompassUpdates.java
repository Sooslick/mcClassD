package ru.sooslick.outlaw;

import ru.sooslick.outlaw.roles.Hunter;

/**
 * Hunters' compass update methods
 */
@SuppressWarnings("unused")
public enum CompassUpdates {
    ALWAYS(Messages.COMPASS_METHOD_ALWAYS, (h) -> {
        h.cooldownTick();
        h.updateCompass();
    }),
    ONCLICK(Messages.COMPASS_METHOD_CLICK, Hunter::cooldownTick),
    NEVER(Messages.COMPASS_METHOD_NEVER, (h) -> {});

    private final CompassUpdateMethod compassUpdateMethod;
    private final String comment;

    CompassUpdates(String comment, CompassUpdateMethod method) {
        this.compassUpdateMethod = method;
        this.comment = comment;
    }

    /**
     * Return the compass update method
     * @return compass update method
     */
    public CompassUpdateMethod getCompassUpdateMethod() {
        return compassUpdateMethod;
    }

    /**
     * Get info message for compass method
     * @return short info message
     */
    public String getComment() {
        return comment;
    }

    /**
     * Functional interface with action that is performed on Hunter who triggers the compass update
     */
    @FunctionalInterface
    public interface CompassUpdateMethod {
        void tick(Hunter h);
    }
}
