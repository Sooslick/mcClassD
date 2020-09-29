package ru.sooslick.outlaw.roles;

import org.bukkit.entity.Player;

public abstract class AbstractPlayer {

    Player player;

    public String getName() {
        return player.getName();
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player p) {
        player = p;
    }

    //todo refactor getLocation()
}
