package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.Map;

public abstract class AbstractPlayer {

    private static final String ADVANCEMENT_REVOKE = "advancement revoke %s everything";

    protected Player player;
    protected boolean firstRespawn = true;

    public AbstractPlayer(Player p) {
        player = p;
    }

    public String getName() {
        return player.getName();
    }

    public Player getPlayer() {
        return player;
    }

    public LivingEntity getEntity() {
        return player;
    }

    public Location getLocation() {
        return player.getLocation();
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
        player.setBedSpawnLocation(null);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(ADVANCEMENT_REVOKE, player.getName()));
        player.setGameMode(GameMode.SURVIVAL);
        if (Cfg.enableStartInventory)
            giveStartInventory();
        onRespawn();
    }

    public void onRespawn() {
        firstRespawn = false;
    }

    public void goOffline() {}

    public void goOnline(Player newPlayer) {
        player = newPlayer;
    }

    public void onEndGame() {
        player.setGameMode(GameMode.SPECTATOR);
        WorldUtil.invToChest(getPlayer().getInventory(), getEntity().getLocation());
    }

    private void giveStartInventory() {
        for (Map.Entry<Material, Integer> e : Cfg.startInventory.entrySet()) {
            player.getInventory().addItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }
}
