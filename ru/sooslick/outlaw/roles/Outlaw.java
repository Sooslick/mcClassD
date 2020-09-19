package ru.sooslick.outlaw.roles;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class Outlaw extends AbstractPlayer {

    Location lastWorldPos;
    Location lastNetherPos;

    public Outlaw(Player p) {
        player = p;
        lastWorldPos = p.getLocation();     //todo: rework, add null check to updateCompass method
        lastNetherPos = p.getLocation();
    }

    public void setLastWorldPos(Location l) {
        lastWorldPos = l;
    }

    public Location getLastWorldPos() {
        return lastWorldPos;
    }

    public void setLastNetherPos(Location l) {
        lastNetherPos = l;
    }

    public Location getLastNetherPos() {
        return lastNetherPos;
    }

    //todo refactor notify mech
}
