package ru.sooslick.outlaw;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.InventoryHolder;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.atomic.AtomicInteger;

public class ChestTracker {
    private static final String REPORT_TEMPLATE = "ChestTracker cleanup report:\nContainers: %s\nBeds: %s\nBlocks: %s\nEntities: %s";
    private static final String TRACKED_FORCE = "Force tracking on block %s";
    private static final String TRACKED_CONTAINER = "Tracked container %s at %s";
    private static final String TRACKED_BED = "Tracked bed at %s";
    private static final String TRACKED_BLOCK = "Tracked important block %s at %s";
    private static final String TRACKED_ENTITY = "Tracked entity %s at %s";

    private static final ArrayList<EntityType> TRACKED_ENTITY_TYPES;
    private static final ArrayList<Material> TRACKED_BLOCKS;

    private final LinkedHashSet<Block> trackedContainers;
    private final LinkedHashSet<Block> trackedBeds;
    private final LinkedHashSet<Block> trackedBlocks;
    private final LinkedHashSet<Entity> trackedEntities;

    static {
        TRACKED_ENTITY_TYPES = new ArrayList<>();
        TRACKED_ENTITY_TYPES.add(EntityType.DROPPED_ITEM);
        TRACKED_ENTITY_TYPES.add(EntityType.MINECART_CHEST);
        TRACKED_ENTITY_TYPES.add(EntityType.MINECART_HOPPER);
        TRACKED_ENTITY_TYPES.add(EntityType.ARMOR_STAND);
        TRACKED_ENTITY_TYPES.add(EntityType.ITEM_FRAME);
        TRACKED_ENTITY_TYPES.add(EntityType.HORSE);
        TRACKED_ENTITY_TYPES.add(EntityType.MULE);
        TRACKED_ENTITY_TYPES.add(EntityType.DONKEY);
        TRACKED_ENTITY_TYPES.add(EntityType.BOAT);

        TRACKED_BLOCKS = new ArrayList<>();
        TRACKED_BLOCKS.add(Material.IRON_ORE);
        TRACKED_BLOCKS.add(Material.IRON_BLOCK);
        TRACKED_BLOCKS.add(Material.GOLD_ORE);
        TRACKED_BLOCKS.add(Material.GOLD_BLOCK);
        TRACKED_BLOCKS.add(Material.COAL_BLOCK);
        TRACKED_BLOCKS.add(Material.DIAMOND_BLOCK);
        TRACKED_BLOCKS.add(Material.ANCIENT_DEBRIS);
        TRACKED_BLOCKS.add(Material.NETHERITE_BLOCK);
        TRACKED_BLOCKS.add(Material.OBSIDIAN);
    }

    public ChestTracker() {
        trackedContainers = new LinkedHashSet<>();
        trackedBeds = new LinkedHashSet<>();
        trackedBlocks = new LinkedHashSet<>();
        trackedEntities = new LinkedHashSet<>();
    }

    public void detectBlock(Block b) {
        detectBlock(b, false);
    }

    public void detectBlock(Block b, boolean force) {
        if (force) {
            if (trackedBlocks.add(b))
                LoggerUtil.debug(String.format(TRACKED_FORCE, WorldUtil.formatLocation(b.getLocation())));
            return;
        }
        if (b.getState() instanceof InventoryHolder) {
            if (trackedContainers.add(b))
                LoggerUtil.debug(String.format(TRACKED_CONTAINER, b.getType(), WorldUtil.formatLocation(b.getLocation())));
        } else if (b.getBlockData() instanceof Bed) {
            if (trackedBeds.add(b))
                LoggerUtil.debug(String.format(TRACKED_BED, WorldUtil.formatLocation(b.getLocation())));
        } else if (TRACKED_BLOCKS.contains(b.getType())) {
            if (trackedBlocks.add(b))
                LoggerUtil.debug(String.format(TRACKED_BLOCK, b.getType(), WorldUtil.formatLocation(b.getLocation())));
        }
    }

    public void detectEntity(Entity e) {
        if (TRACKED_ENTITY_TYPES.contains(e.getType()))
            if (trackedEntities.add(e)) {
                LoggerUtil.debug(String.format(TRACKED_ENTITY, e.getType(), WorldUtil.formatLocation(e.getLocation())));
            }
    }

    public void cleanup() {
        AtomicInteger chests = new AtomicInteger();
        AtomicInteger beds = new AtomicInteger();
        AtomicInteger blocks = new AtomicInteger();
        //clear and delete containers
        trackedContainers.forEach(b -> {
            if (b.getState() instanceof InventoryHolder) {
                ((InventoryHolder) b.getState()).getInventory().clear();
                b.setType(Material.AIR);
                chests.getAndIncrement();
            }
        });
        //clear beds
        trackedBeds.forEach(b -> {
            if (b.getBlockData() instanceof Bed) {
                b.getRelative(((Bed) b.getBlockData()).getFacing()).setType(Material.AIR);
                b.setType(Material.AIR);
                beds.getAndIncrement();
            }
        });
        //clear simple blocks and fluids
        trackedBlocks.forEach(b -> {
            b.setType(Material.AIR);
            blocks.getAndIncrement();
        });
        //clear entities (array is copied to prevent concurrent modification exceptions due to e.remove)
        int ent = 0;
        Entity[] trackerEntitiesArray = new Entity[trackedEntities.size()];
        trackerEntitiesArray = trackedEntities.toArray(trackerEntitiesArray);
        for (Entity e : trackerEntitiesArray) {
            if (e != null) {
                e.remove();
                ent++;
            }
        }
        LoggerUtil.info(String.format(REPORT_TEMPLATE, chests.toString(), beds.toString(), blocks.toString(), ent));
    }
}
