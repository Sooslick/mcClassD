package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.ProtectedNetherPortal;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Representation of the Victim
 */
public class Outlaw extends AbstractPlayer {
    private static final String DEBUG_LINKED_PORTAL = "Portal at %s is linked to the victim";

    private final HashMap<World, Location> trackedPositions;

    private LivingEntity placeholder;
    private boolean offline;
    private int alertTimeoutTimer;
    private ProtectedNetherPortal trackedNetherPortal;

    public Outlaw(Player p) {
        super(p);
        trackedPositions = new HashMap<>();
        placeholder = null;
        offline = false;
        alertTimeoutTimer = 0;
        trackedNetherPortal = null;
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

    /**
     * Update Victim's tracked Nether Portal that will be protected from being destroyed
     * @param portal protected portal
     */
    public void setTrackedNetherPortal(ProtectedNetherPortal portal) {
        LoggerUtil.debug(String.format(DEBUG_LINKED_PORTAL, portal));
        trackedNetherPortal = portal;
    }

    /**
     * Return the protected Nether Portal
     * @return protected Nether Portal
     */
    public ProtectedNetherPortal getTrackedNetherPortal() {
        return trackedNetherPortal;
    }

    @Override
    public void goOffline() {
        placeholder = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), EntityType.CHICKEN);
        placeholder.setGlowing(Cfg.enableVictimGlowing);
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
        try {
            newPlayer.sendMessage(String.format(Messages.VICTIM_REMINDER, Engine.getInstance().getGameMode().getObjective()));
        } catch (Exception e) {
            LoggerUtil.exception(e);
            newPlayer.sendMessage(Messages.VICTIM_REMINDER_DEFAULT);
        }
    }

    @Override
    public void preparePlayer(Location dest) {
        super.preparePlayer(dest);
        if (Cfg.enableStartInventory) {
            for (Map.Entry<Material, Integer> e : Cfg.victimStartInventory.entrySet()) {
                player.getInventory().addItem(new ItemStack(e.getKey(), e.getValue()));
            }
        }
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
