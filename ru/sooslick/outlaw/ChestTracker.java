package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.InventoryHolder;

import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class ChestTracker {

    private static final Logger LOG = Bukkit.getLogger();

    private final LinkedHashSet<Block> trackedContainers;
    private final LinkedHashSet<Block> trackedBeds;
    private final LinkedHashSet<Entity> trackedEntities;

        //todo: can I track minecart with hoppers and chests, frames and armor stands?

    public ChestTracker() {
        trackedContainers = new LinkedHashSet<>();
        trackedBeds = new LinkedHashSet<>();
        trackedEntities = new LinkedHashSet<>();
    }

    public void detectBlock(Block b) {
        if (b.getState() instanceof InventoryHolder) {
            if (trackedContainers.add(b))
                LOG.info("Tracked container at " + b.getLocation().toString());
        } else if (b.getBlockData() instanceof Bed)
            if (trackedBeds.add(b))
                LOG.info("Tracked bed at " + b.getLocation().toString());
    }

    public void detectEntity(Entity e) {
        if (e instanceof InventoryHolder)
            if (trackedEntities.add(e))
                LOG.info("tracked entity at " + e.getLocation());   //todo console will be flooded by these lines, remove or replace it later
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
        LOG.info("ChestTracker cleanup report:\nContainers: " + chests.toString() +
                                                  "\n      Beds: " + beds.toString());
    }

}
