package ru.sooslick.outlaw;

import ru.sooslick.outlaw.roles.Hunter;

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

    public CompassUpdateMethod getCompassUpdateMethod() {
        return compassUpdateMethod;
    }

    @FunctionalInterface
    public interface CompassUpdateMethod {
        void tick(Hunter h);
    }
}
