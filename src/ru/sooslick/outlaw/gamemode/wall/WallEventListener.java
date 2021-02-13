package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.GameState;
import ru.sooslick.outlaw.Messages;
import ru.sooslick.outlaw.util.WorldUtil;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WallEventListener implements Listener {
    private final WallGameModeBase base;

    private boolean firstBlockAlerted;
    private boolean goldenPickaxeAlerted;

    public WallEventListener(WallGameModeBase base) {
        this.base = base;
        reset();
    }

    public void reset() {
        firstBlockAlerted = false;
        goldenPickaxeAlerted = false;
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        //disable nether portals
        e.setCancelled(true);
        //break portal
        Location from = e.getFrom();
        Block b = from.getBlock();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++){
                Block b1 = b.getRelative(x, 0, z);
                if (b1.getType() == Material.NETHER_PORTAL) {
                    b = b1;
                    break;
                }
            }
        }
        b.breakNaturally();
    }

    @EventHandler
    public void onEnderPearl(PlayerTeleportEvent e) {
        //disable teleporting outside playzone
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;

        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location from = e.getFrom();
            World worldFrom = from.getWorld();
            Location to = e.getTo();
            if (to != null && base.isOutside(to)) {
                e.setCancelled(true);
                //effect
                if (worldFrom != null) {
                    worldFrom.playEffect(from, Effect.EXTINGUISH, 4);
                    worldFrom.spawnParticle(Particle.CLOUD, from.add(0, 2, 0), 16);
                }
            }
        }
    }

    @EventHandler
    public void onPistonMove(BlockPistonExtendEvent e) {
        //generate ceiling to prevent escaping by self-lifting
        List<Block> movedBlocks = e.getBlocks().stream()
                .map(b -> b.getRelative(e.getDirection(), 1))
                .collect(Collectors.toList());
        WorldUtil.generateBarrier(movedBlocks);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        //detect wall breaking
        Engine engine = Engine.getInstance();
        if (engine.getGameState() != GameState.GAME)
            return;
        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;

        // detect broken block is inside
        Block b = e.getBlock();
        Location l = b.getLocation();
        int halfsize = base.getHalfSize();
        if ((Math.abs(l.getBlockX()) >= halfsize) || (Math.abs(l.getBlockZ()) >= halfsize)) {
            // alert hunters
            if (!firstBlockAlerted) {
                firstBlockAlerted = true;
                Bukkit.broadcastMessage(Messages.WALL_BREAK_ALERT);
            }
            // disable drops
            if (b.getType() == Material.OBSIDIAN)
                e.setDropItems(false);
            //recalculate Victim's score
            int blocksPassed = Math.max(Math.abs(l.getBlockX()) - halfsize,  Math.abs(l.getBlockZ()) - halfsize) + 1;
            base.setScore(blocksPassed);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        //detect wall restoring
        Block b = e.getBlockPlaced();
        Material m = b.getType();
        if (m == Material.OBSIDIAN || m == Material.NETHERITE_BLOCK || m == Material.CRYING_OBSIDIAN || m == Material.ANCIENT_DEBRIS) {
            int halfsize = base.getHalfSize();
            if ((Math.abs(b.getX()) >= halfsize - 1) || (Math.abs(b.getZ()) >= halfsize - 1)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(Messages.WALL_BUILD_RESTRICTION);
            }
        }

        //detect towering escape attempts
        WorldUtil.generateBarrier(Collections.singletonList(b));
    }

    @EventHandler
    public void onBlockForm(BlockFormEvent e) {
        //detect wall restoring by buckets
        Block b = e.getBlock();
        Material m = b.getType();
        if (m == Material.LAVA) {
            int halfsize = base.getHalfSize();
            if ((Math.abs(b.getX()) >= halfsize - 1) || (Math.abs(b.getZ()) >= halfsize - 1)) {
                e.setCancelled(true);
                b.setType(Material.COBBLESTONE);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        //detect golden pickaxe appearance
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;
        if (e.getWhoClicked().equals(Engine.getInstance().getOutlaw().getPlayer()))
            detectGoldPickaxe(e.getCurrentItem());
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        //detect golden pickaxe appearance
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;
        if (e.getEntity().equals(Engine.getInstance().getOutlaw().getPlayer()))
            detectGoldPickaxe(e.getItem().getItemStack());
    }

    private void detectGoldPickaxe(ItemStack is) {
        if (goldenPickaxeAlerted || is == null)
            return;
        if (is.getType() == Material.GOLDEN_PICKAXE) {
            goldenPickaxeAlerted = true;
            Bukkit.broadcastMessage(Messages.WALL_GOLDEN_PICKAXE_ALERT);
        }
    }
}
