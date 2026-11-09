/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Material;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class AntiCountBug
implements Listener {
    NeverLag plugin;

    public AntiCountBug(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onBlockDispense(BlockDispenseEvent e) {
        if (e.isCancelled()) {
            return;
        }
        Inventory content = null;
        if (e.getBlock().getType() == Material.DROPPER) {
            content = ((Dropper)e.getBlock().getState()).getInventory();
        } else if (e.getBlock().getType() == Material.DISPENSER) {
            content = ((Dispenser)e.getBlock().getState()).getInventory();
        }
        if (content == null || content.getSize() == 0) {
            return;
        }
        int count = content.getSize();
        int i = 0;
        while (i < count) {
            if (content.getItem(i) != null && content.getItem(i).getType() != Material.AIR && content.getItem(i).getAmount() < 0) {
                content.getItem(i).setType(Material.AIR);
                e.setCancelled(true);
            }
            ++i;
        }
        count = 0;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getCurrentItem() != null) {
            if (e.getCurrentItem().getType() != Material.AIR && e.getCurrentItem().getAmount() <= 0) {
                e.getCurrentItem().setType(Material.AIR);
                e.setCancelled(true);
            }
        } else if (e.getCursor() != null && e.getCursor().getType() != Material.AIR && e.getCursor().getAmount() <= 0) {
            e.getCursor().setType(Material.AIR);
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (e.isCancelled()) {
            return;
        }
        ItemStack[] itemStackArray = e.getInventory().getContents();
        int n = itemStackArray.length;
        int n2 = 0;
        while (n2 < n) {
            ItemStack item = itemStackArray[n2];
            if (item != null && item.getType() != Material.AIR && item.getAmount() <= 0) {
                item.setType(Material.AIR);
            }
            ++n2;
        }
    }

    @EventHandler
    public void onSpawn(ItemSpawnEvent e) {
        if (e.getEntity().getItemStack().getAmount() < 0) {
            e.setCancelled(true);
        }
    }
}

