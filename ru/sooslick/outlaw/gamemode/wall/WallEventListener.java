package ru.sooslick.outlaw.gamemode.wall;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.Engine;
import ru.sooslick.outlaw.GameState;
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
    }

    @EventHandler
    public void onEnderPearl(PlayerTeleportEvent e) {
        //disable teleporting outside playzone
        if (Engine.getInstance().getGameState() != GameState.GAME)
            return;

        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location l = e.getTo();
            if (base.isOutside(l)) {
                //todo: create placeholder effect
                e.setCancelled(true);
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

        //recalculate wall remainder
        base.recalculateScore(e.getBlock().getLocation());

        if (firstBlockAlerted)
            return;

        //alert first block
        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;
        Location l = e.getBlock().getLocation();
        int halfsize = base.getHalfSize();
        if ((Math.abs(l.getBlockX()) >= halfsize) || (Math.abs(l.getBlockZ()) >= halfsize)) {
            firstBlockAlerted = true;
            Bukkit.broadcastMessage("§cVictim is trying to break the Wall");
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
                e.getPlayer().sendMessage("§4Obsidian is restricted here");
            }
        }

        //detect towering escape attempts
        WorldUtil.generateBarrier(Collections.singletonList(b));
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
        if (goldenPickaxeAlerted)
            return;
        if (is.getType() == Material.GOLDEN_PICKAXE) {
            goldenPickaxeAlerted = true;
            Bukkit.broadcastMessage("§cGolden pickaxe detected");
        }
    }
}
