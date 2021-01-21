package ru.sooslick.outlaw;

public class TimedRequest {

    private final static int DEFAULT_TIMER = 60;

    private int timer;
    private boolean active;

    public TimedRequest() {
        timer = DEFAULT_TIMER;
        active = true;
    }

    public boolean isActive() {
        return active;
    }

    public void deactivate() {
        active = false;
    }

    //returns is request deactivated by timer
    public boolean tick() {
        if (active)
            if (--timer <= 0) {
                deactivate();
                return true;
            }
        return false;
    }
}
