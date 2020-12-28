package ru.sooslick.outlaw.roles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import ru.sooslick.outlaw.Cfg;
import ru.sooslick.outlaw.CompassUpdates;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.CommonUtil;

import java.time.Duration;

public class Hunter extends AbstractPlayer {
    private static Outlaw outlaw;
    private static CompassUpdates.CompassUpdateMethod compassUpdateMethod;

    public static void setupHunter(Outlaw o) {
        outlaw = o;
        compassUpdateMethod = Cfg.compassUpdates.getCompassUpdateMethod();
    }

    private int compassCooldown;

    public Hunter(Player p) {
        super(p);
        compassCooldown = 0;
    }

    @Override
    public void preparePlayer(Location dest) {
        super.preparePlayer(dest);
        updateCompass();            //todo first update dont reset cooldown
    }

    @Override
    public void onRespawn() {
        if (!firstRespawn) {
            Engine e = Engine.getInstance();
            player.sendMessage(String.format(Messages.HUNTER_RESPAWN, e.getOutlaw().getName(), CommonUtil.formatDuration(Duration.ofSeconds(e.getGameTimer()))));
        }
        if (Cfg.compassUpdates != CompassUpdates.NEVER)
            player.getInventory().addItem(new ItemStack(Material.COMPASS));
        firstRespawn = false;
    }

    public void triggerCompassUpdateTick() {
        compassUpdateMethod.tick(this);
    }

    public void cooldownTick() {
        compassCooldown--;
    }

    public boolean updateCompass() {
        if (compassCooldown > 0) {
            return false;
        }

        Location trackedLocation = outlaw.getTrackedLocation(player.getWorld());   //todo: uneffective (???) use of getTrackedLocation
        //unusual scenario, e.g. hunters teleported to nether but victim not. Just do nothing
        if (trackedLocation == null) {
            return false;
        }
        player.setCompassTarget(trackedLocation);
        //ignore the fact that compass or lodestone may lack, force cooldown here
        compassCooldown = Cfg.compassUpdatesPeriod;

        //update Nbt
        //first: find Compass item in inventory
        Inventory inv = player.getInventory();
        ItemStack is = null;
        boolean updateName = true;
        //find any compass in inventory, preferably compass given by plugin (Victim Tracker)
        for (ItemStack current : inv.getContents()) {
            //ItemStack current CAN be null
            //noinspection ConstantConditions
            if (current != null && current.getType() == Material.COMPASS) {
                is = current;
                ItemMeta ism = is.getItemMeta();
                if (ism != null) {
                    if (ism.getDisplayName().equals(Messages.COMPASS_NAME)) {
                        updateName = false;
                        break;
                    }
                }
            }
        }
        if (is == null) {
            return false;
        }

        //second: get compass meta
        CompassMeta meta;
        if (is.getItemMeta() instanceof CompassMeta)
            meta = (CompassMeta) is.getItemMeta();
        else {
            return false;
        }

        //optional: set name to compass
        if (updateName)
            meta.setDisplayName(Messages.COMPASS_NAME);

        //third: update meta
        if (trackedLocation.getWorld().getEnvironment() == World.Environment.NETHER) {
            //create lodestone in nether (VERY WEIRD SOLUTION BUT WORKS ONLY)
            trackedLocation.setY(128);
            trackedLocation.getBlock().setType(Material.LODESTONE);
            //todo: uneffective setBlock: called every updateCompass for every hunter
            meta.setLodestone(trackedLocation);
            meta.setLodestoneTracked(true);
        } else {
            //or reset for overworld
            meta.setLodestone(null);
            meta.setLodestoneTracked(false);
        }
        //and finally set meta
        is.setItemMeta(meta);
        return true;
    }

}
