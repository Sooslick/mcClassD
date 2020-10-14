package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.inventory.InventoryHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ChestTracker {

    private static final ArrayList<Material> CONTAINERS;
    private static final Logger LOG;

    private final LinkedHashSet<Block> trackedContainers;
    private final LinkedHashSet<Block> trackedBeds;

    static {
        LOG = Bukkit.getLogger();
        Material[] containers = {Material.CHEST, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.BARREL,
                Material.DISPENSER, Material.DROPPER, Material.BREWING_STAND, Material.HOPPER, Material.TRAPPED_CHEST,
                Material.ENDER_CHEST};
        //todo: can I track minecart with hoppers and chests, frames and armor stands?
        CONTAINERS = new ArrayList<>(Arrays.asList(containers));
    }

    public ChestTracker() {
        trackedContainers = new LinkedHashSet<>();
        trackedBeds = new LinkedHashSet<>();
    }

    public void detectBlock(Block b) {
        if (CONTAINERS.contains(b.getType())) {
            if (trackedContainers.add(b))
                LOG.info("Tracked container at " + b.getLocation().toString());
        } else if (b.getBlockData() instanceof Bed)
            if (trackedBeds.add(b))
                LOG.info("Tracked bed at " + b.getLocation().toString());
    }

    public void cleanup() {
        AtomicInteger chests = new AtomicInteger();
        AtomicInteger beds = new AtomicInteger();
        trackedContainers.forEach(b -> {
            if (b.getState() instanceof InventoryHolder) {
                ((InventoryHolder) b.getState()).getInventory().clear();
                b.setType(Material.AIR);
                chests.getAndIncrement();
            }
        });
        trackedBeds.forEach(b -> {
            if (b.getBlockData() instanceof Bed) {
                b.setType(Material.AIR);            //todo prevent drop
                beds.getAndIncrement();
            }
        });
        LOG.info("ChestTracker cleanup report:\nContainers: " + chests.toString() + "\n      Beds: " + beds.toString());
    }

}
