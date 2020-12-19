package ru.sooslick.outlaw.roles;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Team;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.CommonUtil;

import java.time.Duration;

public class Hunter extends AbstractPlayer {

    private static Location spawnLocation;
    private static Team huntersTeam;

    public static void setupTeam(Team team, Location spawn) {
        spawnLocation = spawn;
        huntersTeam = team;
        spawnLocation.getWorld().setSpawnLocation(spawnLocation); //for new players and respawns
    }

    public static Team getTeam() {
        return huntersTeam;
    }

    public static Location getSpawnLocation() {
        return spawnLocation;
    }

    public Hunter(Player p) {
        super(p);
    }

    @Override
    public void onRespawn() {
        if (!firstRespawn) {
            Engine e = Engine.getInstance();
            player.sendMessage(String.format(Messages.HUNTER_RESPAWN, e.getOutlaw().getName(), CommonUtil.formatDuration(Duration.ofSeconds(e.getGameTimer()))));
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
        boolean updateName = true;
        //find any compass in inventory, preferably compass given by plugin (Victim Tracker)
        for (ItemStack current : inv.getContents()) {
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
            return;
        }

        //second: get compass meta
        CompassMeta meta;
        if (is.getItemMeta() instanceof CompassMeta)
            meta = (CompassMeta) is.getItemMeta();
        else {
            return;
        }

        //optional: set name to compass
        if (updateName)
            meta.setDisplayName(Messages.COMPASS_NAME);

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
        }
    }
}
