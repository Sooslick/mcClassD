package ru.sooslick.outlaw.roles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

public class Hunter extends AbstractPlayer {

    public Hunter(Player p) {
        super(p);
    }

    @Override
    public void onRespawn() {
        if (!firstRespawn) {
            //player.sendMessage(); todo send message who is victim and etc.
            //                          I can get this messages only by callin Engine
            //                          It's easier just implement INSTANCE field in Engine
            //                          and refactor all constructors with Engine param
        }
        player.getInventory().addItem(new ItemStack(Material.COMPASS));
        firstRespawn = false;
    }

    public void updateCompass(Outlaw outlaw) {
        Location trackedLocation = outlaw.getTrackedLocation(player.getWorld());   //todo: uneffective (???) use of getTrackedLocation
        player.setCompassTarget(trackedLocation);

        //update Nbt
        //first: find Compass item in inventory
        Inventory inv = player.getInventory();
        ItemStack is = null;
        for (ItemStack current : inv.getContents()) {
            if (current != null && current.getType() == Material.COMPASS) {
                is = current;
                break;
            }
        }
        if (is == null) {
            //todo check debug
            //Bukkit.getLogger().info("Cross-world update compass. Item not found, player " + player.getName());
            return;
        }

        //second: get compass meta
        CompassMeta meta;
        if (is.getItemMeta() instanceof CompassMeta)
            meta = (CompassMeta) is.getItemMeta();
        else {
            //Bukkit.getLogger().info("Cross-world update compass. Compass meta not found, player " + player.getName());
            return;
        }

        //finally: update meta
        if (trackedLocation.getWorld().getEnvironment() == World.Environment.NETHER) {
            //create lodestone (VERY WEIRD SOLUTION BUT WORKS ONLY)
            trackedLocation.setY(128);
            trackedLocation.getBlock().setType(Material.LODESTONE);
            //todo: uneffective setBlock: called every updateCompass for every hunter

            //and finally set meta
            meta.setLodestone(trackedLocation);
            meta.setLodestoneTracked(true);
            is.setItemMeta(meta);
            //Bukkit.getLogger().info("Cross-world update compass. Set meta for player " + player.getName());
        } else {
            meta.setLodestone(null);
            meta.setLodestoneTracked(false);
            is.setItemMeta(meta);
            //Bukkit.getLogger().info("Cross-world update compass. Reset meta for player " + player.getName());
        }
    }
}
