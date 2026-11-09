/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;

public class AntiChestBug
implements Listener {
    NeverLag plugin;

    public AntiChestBug(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        InventoryHolder inventory;
        if (e.isCancelled()) {
            return;
        }
        if (e.getBlock().getState() instanceof InventoryHolder && (inventory = (InventoryHolder)e.getBlock().getState()).getInventory().getViewers().size() > 0) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(NeverLag.m_antichest);
        }
    }
}

