package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class Outlaw extends AbstractPlayer {

    Location lastWorldPos;
    Location lastNetherPos;
    LivingEntity placeholder;
    boolean offline;

    public Outlaw(Player p) {
        super(p);
        lastWorldPos = p.getLocation();     //todo: rework, add null check to updateCompass method
        lastNetherPos = p.getLocation();
        placeholder = null;
        offline = false;
    }

    public void setLastWorldPos(Location l) {
        lastWorldPos = l;
    }

    public void setLastNetherPos(Location l) {
        lastNetherPos = l;
    }

    public Location getTrackedLocation(World from) {
        //method doesn't work correctly with custom worlds?

        //check if victim's and hunter's worlds equals
        Location here = getLocation();
        if (here.getWorld().equals(from))
            return here;

        //else return last tracked position (i.e. portal position) in this world
        World.Environment env = from.getEnvironment();
        switch (env) {
            case NORMAL:
                return lastWorldPos;
            case NETHER:
                return lastNetherPos;
            default:
                return here;
        }

    }

    public void goOffline(LivingEntity e) {
        offline = true;
        placeholder = e;
        Bukkit.broadcastMessage("§cVictim left the game, but there is §eVictim Chicken§c. Kill it!");
    }

    public void goOnline(Player p) {
        placeholder.remove();
        player = p;
        offline = false;
        placeholder = null;
        Bukkit.broadcastMessage("§cVictim returns back to the game.");
    }

    @Override
    public LivingEntity getEntity() {
        return offline ? placeholder : player;
    }

    //todo refactor notify mech
}
