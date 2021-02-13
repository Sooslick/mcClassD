package ru.sooslick.outlaw;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.InventoryHolder;
import ru.sooslick.outlaw.util.LoggerUtil;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Class for tracking and rolling back players' stuff
 */
public class ChestTracker {
    private static final String REPORT_TEMPLATE = "ChestTracker cleanup report:\nContainers: %s\nBeds: %s\nBlocks: %s\nEnd frames: %s\nEntities: %s";
    private static final String TRACKED_FORCE = "Force tracking on block %s";
    private static final String TRACKED_CONTAINER = "Tracked container %s at %s";
    private static final String TRACKED_BED = "Tracked bed at %s";
    private static final String TRACKED_BLOCK = "Tracked important block %s at %s";
    private static final String TRACKED_FRAME = "Tracked end portal frame at %s";
    private static final String TRACKED_ENTITY = "Tracked entity %s at %s";

    private static final ArrayList<EntityType> TRACKED_ENTITY_TYPES;
    private static final ArrayList<Material> TRACKED_BLOCKS;

    private final LinkedHashSet<Block> trackedContainers;
    private final LinkedHashSet<Block> trackedBeds;
    private final LinkedHashSet<Block> trackedBlocks;
    private final LinkedHashSet<Block> trackedEndFrames;
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

    ChestTracker() {
        trackedContainers = new LinkedHashSet<>();
        trackedBeds = new LinkedHashSet<>();
        trackedBlocks = new LinkedHashSet<>();
        trackedEntities = new LinkedHashSet<>();
        trackedEndFrames = new LinkedHashSet<>();
    }

    /**
     * Detect specified block and mark it for rollback if it meets rollback criteria
     * @param b tracked block
     */
    public void detectBlock(Block b) {
        detectBlock(b, false);
    }

    /**
     * Detect specified block and mark it for rollback if it meets rollback criteria
     * @param b tracked block
     * @param force forced track flag
     */
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
        } else if (b.getType() == Material.END_PORTAL_FRAME) {
            if (trackedEndFrames.add(b))
                LoggerUtil.debug(String.format(TRACKED_FRAME, WorldUtil.formatLocation(b.getLocation())));
        }
    }

    /**
     * Detect the specified entity and mark it for rollback if it meets rollback criteria
     * @param e tracked entity
     */
    public void detectEntity(Entity e) {
        if (TRACKED_ENTITY_TYPES.contains(e.getType()))
            if (trackedEntities.add(e)) {
                LoggerUtil.debug(String.format(TRACKED_ENTITY, e.getType(), WorldUtil.formatLocation(e.getLocation())));
            }
    }

    /**
     * Remove all tracked stuff
     */
    public void cleanup() {
        int chests = trackedContainers.size();
        int beds = trackedBeds.size();
        int blocks = trackedBlocks.size();
        int ent = trackedEntities.size();
        int frames = trackedEndFrames.size();
        //clear and delete containers
        trackedContainers.forEach(b -> {
            if (b.getState() instanceof InventoryHolder) {
                ((InventoryHolder) b.getState()).getInventory().clear();
                b.setType(Material.AIR);
            }
        });
        trackedContainers.clear();
        //clear beds
        trackedBeds.forEach(b -> {
            if (b.getBlockData() instanceof Bed) {
                b.getRelative(((Bed) b.getBlockData()).getFacing()).setType(Material.AIR);
                b.setType(Material.AIR);
            }
        });
        trackedBeds.clear();
        //clear simple blocks and fluids
        trackedBlocks.forEach(b -> b.setType(Material.AIR));
        trackedBlocks.clear();
        //rollback end frames
        trackedEndFrames.forEach(b -> {
            if (b.getBlockData() instanceof EndPortalFrame) {
                EndPortalFrame epf = (EndPortalFrame) b.getBlockData();
                epf.setEye(false);
                b.setBlockData(epf);
                //track nearby blocks to remove end gateway
                b.getRelative(epf.getFacing(), 1).setType(Material.AIR);
                b.getRelative(epf.getFacing(), 2).setType(Material.AIR);
                b.getRelative(epf.getFacing(), 3).setType(Material.AIR);
            }
        });
        //clear entities (via iterator because concurrent modification exception)
        Iterator<Entity> i = trackedEntities.iterator();
        //noinspection WhileLoopReplaceableByForEach
        while (i.hasNext()) {
            Entity e = i.next();
            if (e != null)
                e.remove();
        }
        LoggerUtil.info(String.format(REPORT_TEMPLATE, chests, beds, blocks, frames, ent));
    }
}
