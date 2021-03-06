package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.HashMap;

/**
 * Representation of the Victim
 */
public class Outlaw extends AbstractPlayer {
    private final HashMap<World, Location> trackedPositions;

    private LivingEntity placeholder;
    private boolean offline;
    private int alertTimeoutTimer;

    public Outlaw(Player p) {
        super(p);
        trackedPositions = new HashMap<>();
        placeholder = null;
        offline = false;
        alertTimeoutTimer = 0;
    }

    @Override
    public LivingEntity getEntity() {
        return offline ? placeholder : player;
    }

    @Override
    public void onEndGame() {
        super.onEndGame();
        if (placeholder != null) placeholder.remove();
    }

    /**
     * Update Victim's tracked position for Hunters' compasses
     * @param l location to track
     */
    public void setTrackedLocation(Location l) {
        trackedPositions.put(l.getWorld(), l);
    }

    /**
     * Return the tracked location for specified world
     * @param from requested world
     * @return tracked location for this world
     */
    public Location getTrackedLocation(World from) {
        //check if victim's and hunter's worlds equals
        Location here = getLocation();
        if (from.equals(here.getWorld()))
            return here;

        //else return last tracked position (i.e. portal position) in this world
        return trackedPositions.get(from);
    }

    @Override
    public void goOffline() {
        placeholder = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.CHICKEN);
        placeholder.setAI(false);
        placeholder.setCustomName(player.getName());
        offline = true;
        Bukkit.broadcastMessage(Messages.VICTIM_OFFLINE);
    }

    @Override
    public void goOnline(Player newPlayer) {
        super.goOnline(newPlayer);
        placeholder.remove();
        offline = false;
        placeholder = null;
        Bukkit.broadcastMessage(Messages.VICTIM_ONLINE);
        newPlayer.sendMessage(String.format(Messages.VICTIM_REMINDER, Engine.getInstance().getGameMode().getObjective()));
    }

    /**
     * Send text notification for Victim if hunters are nearby
     */
    public void huntersNearbyAlert() {
        if (--alertTimeoutTimer > 0)
            return;

        LivingEntity outlawPlayer = getEntity();
        Location outlawLocation = outlawPlayer.getLocation();
        for (Hunter h : Engine.getInstance().getHunters()) {
            if (!h.getPlayer().getWorld().equals(outlawLocation.getWorld()))
                continue;
            if (WorldUtil.distance2d(h.getLocation(), outlawLocation) < Cfg.alertRadius) {
                alertTimeoutTimer = Cfg.alertTimeout;
                outlawPlayer.sendMessage(Messages.HUNTERS_NEARBY);
                //glow placeholder entity if Outlaw player is offline
                if (!(outlawPlayer instanceof Player)) {
                    outlawPlayer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, Cfg.alertTimeout * 20, 3));
                }
                return;
            }
        }
    }
}
