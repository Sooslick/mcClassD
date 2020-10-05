package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class Outlaw extends AbstractPlayer {

    Location lastWorldPos;
    Location lastNetherPos;
    LivingEntity placeholder;
    boolean offline;

    public Outlaw(Player p) {
        player = p;
        lastWorldPos = p.getLocation();     //todo: rework, add null check to updateCompass method
        lastNetherPos = p.getLocation();
        placeholder = null;                 //todo refactor: single field for player and placeholder
        offline = false;
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

    public LivingEntity getRepresentative() {
        return offline ? placeholder : player;
    }

    //todo refactor notify mech
}
