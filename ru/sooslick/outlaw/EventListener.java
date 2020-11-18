package ru.sooslick.outlaw;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.sooslick.outlaw.roles.Hunter;
import ru.sooslick.outlaw.roles.Outlaw;

public class EventListener implements Listener {

    private Engine engine;
    private boolean firstBlockAlerted;
    private boolean goldenPickaxeAlerted;

    public EventListener(Engine engine) {
        this.engine = engine;
        reset();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check if dragon dead
        if (e.getEntity().getType().equals(EntityType.ENDER_DRAGON)) {
            if (((LivingEntity) e.getEntity()).getHealth() - e.getFinalDamage() <= 0) {
                Bukkit.broadcastMessage("§cDragon died. §eVictim escaped!");            //todo: impl method victory in Engine
                engine.changeGameState(GameState.IDLE);
            }
            return;
        }

        //check if outlaw dead
        LivingEntity outlaw = engine.getOutlaw().getRepresentative();
        if (!e.getEntity().equals(outlaw))
            return;
        if (outlaw.getHealth() - e.getFinalDamage() <= 0) {
            e.setCancelled(true);
            if (outlaw instanceof Player) {
                Location l = outlaw.getLocation();
                Util.invToChest(((Player) outlaw).getInventory(), l);
                //todo is possible to steal inventory while outlaw is offline?
                engine.getChestTracker().detectBlock(l.getBlock());
                engine.getChestTracker().detectBlock(l.add(0, 1, 0).getBlock());
            }
            Bukkit.broadcastMessage("§cVictim died. §eHunters win!");   //todo: impl method victory in Engine
            engine.changeGameState(GameState.IDLE);
        }

        //todo: check if hunter s ded
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            return;

        //check outlaw
        Entity eventEntity = e.getEntity();
        LivingEntity outlaw = engine.getOutlaw().getRepresentative();
        if (e.getEntity().equals(outlaw))
            return;

        //else inc killcounter
        if (eventEntity instanceof Player)
            engine.incKill();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        e.getPlayer().getInventory().addItem(new ItemStack(Material.COMPASS));
        //todo adequate fix
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (Cfg.enableEscapeGamemode) {
            e.setCancelled(true);
            return;
        }
        Outlaw o = engine.getOutlaw();
        if (!e.getPlayer().equals(o.getPlayer()))
            return;
        Location from = e.getFrom();
        World.Environment env = from.getWorld().getEnvironment();
        if (env.equals(World.Environment.NORMAL))
            o.setLastWorldPos(from);
        else if (env.equals(World.Environment.NETHER))
            o.setLastNetherPos(from);
    }

    @EventHandler
    public void onEnderPearl(PlayerTeleportEvent e) {
        if (!Cfg.enableEscapeGamemode) {
            return;
        }
        if (e.getCause().equals(PlayerTeleportEvent.TeleportCause.ENDER_PEARL)) {
            Location l = e.getTo();
            if (engine.isOutside(l))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!Cfg.enableEscapeGamemode)
            return;
        if (!engine.getGameState().equals(GameState.GAME))
            return;
        if (firstBlockAlerted)
            return;

        Player p = e.getPlayer();
        if (!p.equals(engine.getOutlaw().getPlayer()))
            return;
        Location l = e.getBlock().getLocation();
        int halfsize = engine.getHalfSize();
        if ((Math.abs(l.getBlockX()) >= halfsize) || (Math.abs(l.getBlockZ()) >= halfsize)) {
            firstBlockAlerted = true;
            Bukkit.broadcastMessage("§cVictim is trying to break the Wall");       //todo refactor broadcast to broadcaster class
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        //detect beds and chests
        Block b = e.getBlockPlaced();
        engine.getChestTracker().detectBlock(b);

        if (!Cfg.enableEscapeGamemode)
            return;

        //detect wall restoring
        Material m = b.getType();
        if (m == Material.OBSIDIAN || m == Material.NETHERITE_BLOCK || m == Material.CRYING_OBSIDIAN || m == Material.ANCIENT_DEBRIS) {
            int halfsize = engine.getHalfSize();
            if ((Math.abs(b.getX()) >= halfsize - 1) || (Math.abs(b.getZ()) >= halfsize - 1)) {
                e.setCancelled(true);
                e.getPlayer().sendMessage("§4Obsidian is denied here");
            }
        }

        //detect towering escape attempts
        if (b.getY() > 240) {
            //generate weird barrier for wall gamemode to prevent escape over the wall
            World w = b.getWorld();
            w.getBlockAt(b.getX(), 255, b.getZ()).setType(Material.BARRIER);
            w.getBlockAt(b.getX() - 1, 255, b.getZ()).setType(Material.BARRIER);
            w.getBlockAt(b.getX(), 255, b.getZ() - 1).setType(Material.BARRIER);
            w.getBlockAt(b.getX() + 1, 255, b.getZ()).setType(Material.BARRIER);
            w.getBlockAt(b.getX(), 255, b.getZ() + 1).setType(Material.BARRIER);
            //todo: piston exploit
        }
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent e) {
        if (engine.getGameState() != GameState.GAME)
            return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        engine.getChestTracker().detectBlock(e.getClickedBlock());
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (engine.getGameState() != GameState.GAME)
            return;
        engine.getChestTracker().detectEntity(e.getRightClicked());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!engine.getGameState().equals(GameState.GAME)) {
            //todo infomessages
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
            return;
        }
        Player p = e.getPlayer();
        Outlaw o = engine.getOutlaw();

        //check Outlaw
        if (p.getName().equals(o.getPlayer().getName())) {
            o.goOnline(p);
            return;
        }

        //check hunter
        for (Hunter h : engine.getHunters()) {
            if (h.getPlayer().getName().equals(p.getName())) {
                h.setPlayer(p);
                return;
            }
        }

        //set spectator mode for anyone else
        p.setGameMode(GameMode.SPECTATOR);
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent e) {
        if (!engine.getGameState().equals(GameState.GAME))
            //todo: remove from vs / suggest
            return;
        Player p = e.getPlayer();
        Outlaw o = engine.getOutlaw();
        if (o.getPlayer().equals(p)) {
            LivingEntity entity = (LivingEntity) p.getWorld().spawnEntity(p.getLocation(), EntityType.CHICKEN);
            entity.setAI(false);
            entity.setCustomName(p.getName());
            o.goOffline(entity);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        //todo just understand how it works and do adequate impl
        detectGoldPickaxe();
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent e) {
        detectGoldPickaxe();    //todo fix it too
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent e) {
        //ALWAYS detect entities!
        //ChestTracker created after changeGameState - Game and its value is null before first game
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            engine.getChestTracker().detectEntity(e.getEntity());
    }

    @EventHandler
    public void onVehicleSpawn(VehicleCreateEvent e) {
        //ALWAYS detect entities!
        //ChestTracker created after changeGameState - Game and its value is null before first game
        ChestTracker ct = engine.getChestTracker();
        if (ct != null)
            engine.getChestTracker().detectEntity(e.getVehicle());
    }

    public void reset() {
        firstBlockAlerted = false;
        goldenPickaxeAlerted = false;
    }

    private void detectGoldPickaxe() {
        if (!engine.getGameState().equals(GameState.GAME))
            return;
        if (goldenPickaxeAlerted)
            return;
        Inventory inv = engine.getOutlaw().getPlayer().getInventory();
        for (ItemStack is : inv.getContents())
            if (is != null)
                if (is.getType() == Material.GOLDEN_PICKAXE) {
                    goldenPickaxeAlerted = true;
                    Bukkit.broadcastMessage("§cGolden pickaxe detected");       //todo refactor broadcast to broadcaster class
                }
    }
}
