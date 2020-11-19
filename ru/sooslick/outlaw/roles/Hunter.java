package ru.sooslick.outlaw.roles;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
}
