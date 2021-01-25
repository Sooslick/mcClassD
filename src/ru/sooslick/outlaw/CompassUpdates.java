package ru.sooslick.outlaw;

import ru.sooslick.outlaw.roles.Hunter;

/**
 * Hunters' compass update methods
 */
@SuppressWarnings("unused")
public enum CompassUpdates {
    ALWAYS((h) -> {
        h.cooldownTick();
        h.updateCompass();
    }),
    ONCLICK(Hunter::cooldownTick),
    NEVER((h) -> {});

    private final CompassUpdateMethod compassUpdateMethod;

    CompassUpdates(CompassUpdateMethod method) {
        compassUpdateMethod = method;
    }

    /**
     * Return the compass update method
     * @return compass update method
     */
    public CompassUpdateMethod getCompassUpdateMethod() {
        return compassUpdateMethod;
    }

    /**
     * Functional interface with action that is performed on Hunter who triggers the compass update
     */
    @FunctionalInterface
    public interface CompassUpdateMethod {
        void tick(Hunter h);
    }
}
