package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.Cfg;

import java.util.Map;

public abstract class AbstractPlayer {

    protected Player player;

    public AbstractPlayer(Player p) {
        player = p;
    }

    public String getName() {
        return player.getName();
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player p) {
        player = p;
    }

    public Location getLocation() {
        return player.getLocation();
        //todo: test method with offline players
    }

    public void preparePlayer(Location dest) {
        player.teleport(dest);
        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(5);
        player.setExhaustion(0);
        player.setTotalExperience(0);
        player.setExp(0);
        player.setLevel(0);
        player.getInventory().clear();
        player.getActivePotionEffects().clear();
        player.setBedSpawnLocation(dest);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "advancement revoke " + player.getName() + " everything");
        player.setGameMode(GameMode.SURVIVAL);
        if (Cfg.enableStartInventory)
            giveStartInventory();
        //todo trigger onRespawn()
    }

    private void giveStartInventory() {
        for (Map.Entry<Material, Integer> e : Cfg.startInventory.entrySet()) {
            player.getInventory().addItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }
}
