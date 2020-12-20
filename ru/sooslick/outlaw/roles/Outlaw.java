package ru.sooslick.outlaw.roles;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.CommonUtil;

public class Outlaw extends AbstractPlayer {

    Location lastWorldPos;
    Location lastNetherPos;
    LivingEntity placeholder;       //todo: cleanup in Engine.onDisable
    boolean offline;
    int alertTimeoutTimer;

    public Outlaw(Player p) {
        super(p);
        lastWorldPos = p.getLocation();     //todo: rework, add null check to updateCompass method
        lastNetherPos = p.getLocation();
        placeholder = null;
        offline = false;
        alertTimeoutTimer = 0;
    }

    @Override
    public LivingEntity getEntity() {
        return offline ? placeholder : player;
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
        Bukkit.broadcastMessage(Messages.VICTIM_OFFLINE);
    }

    public void goOnline(Player p) {
        placeholder.remove();
        player = p;
        offline = false;
        placeholder = null;
        Bukkit.broadcastMessage(Messages.VICTIM_ONLINE);
    }

    public void huntersNearbyAlert() {
        if (--alertTimeoutTimer > 0)
            return;

        LivingEntity outlawPlayer = getEntity();
        Location outlawLocation = outlawPlayer.getLocation();
        for (Hunter h : Engine.getInstance().getHunters()) {
            if (!h.getPlayer().getWorld().equals(outlawLocation.getWorld()))
                continue;
            if (CommonUtil.distance(h.getLocation(), outlawLocation) < Cfg.alertRadius) {
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
