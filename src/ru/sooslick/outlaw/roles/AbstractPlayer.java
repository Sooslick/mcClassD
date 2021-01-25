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

/**
 * Representation of Manhunt player
 */
public abstract class AbstractPlayer {

    private static final String ADVANCEMENT_REVOKE = "advancement revoke %s everything";

    protected Player player;
    protected boolean firstRespawn = true;

    public AbstractPlayer(Player p) {
        player = p;
    }

    /**
     * Return player's name
     * @return player's name
     */
    public String getName() {
        return player.getName();
    }

    /**
     * Return the Minecraft player
     * @return Minecraft player
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * Return the entity representing the Manhunt player
     * @return LivingEntity representing the Manhunt player
     */
    public LivingEntity getEntity() {
        return player;
    }

    /**
     * Return the current location of Manhunt player
     * @return the location of Manhunt player
     */
    public Location getLocation() {
        return player.getLocation();
    }

    /**
     * Spawn the Manhunt player at specified location and prepare him for the game
     * @param dest location where the Manhunt player will spawn
     */
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
        player.getActivePotionEffects().forEach(pe -> player.removePotionEffect(pe.getType()));
        player.setBedSpawnLocation(null);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), String.format(ADVANCEMENT_REVOKE, player.getName()));
        player.setGameMode(GameMode.SURVIVAL);
        if (Cfg.enableStartInventory)
            giveStartInventory();
        onRespawn();
    }

    /**
     * Do action after respawn
     */
    public void onRespawn() {
        firstRespawn = false;
    }

    /**
     * Do action after leaving the server
     */
    public void goOffline() {}

    /**
     * Do action after rejoining the server
     * @param newPlayer same Minecraft player
     */
    public void goOnline(Player newPlayer) {
        player = newPlayer;
    }

    /**
     * Do action after the game ending
     */
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
