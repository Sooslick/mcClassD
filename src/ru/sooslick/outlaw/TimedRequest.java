package ru.sooslick.outlaw;

class TimedRequest {

    private final static int DEFAULT_TIMER = 60;

    private int timer;
    private boolean active;

    TimedRequest() {
        timer = DEFAULT_TIMER;
        active = true;
    }

    boolean isActive() {
        return active;
    }

    void deactivate() {
        active = false;
    }

    //returns is request deactivated by timer
    boolean tick() {
        if (active)
            if (--timer <= 0) {
                deactivate();
                return true;
            }
        return false;
    }
}
