/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.plugin.Plugin;

public class AntiNetherHopperCheat
implements Listener {
    NeverLag plugin;

    public AntiNetherHopperCheat(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onHopper(InventoryMoveItemEvent e) {
        Hopper hopper;
        if (e.getInitiator().getHolder() instanceof Hopper && (hopper = (Hopper)e.getInitiator().getHolder()).getWorld().getName().equalsIgnoreCase("world_nether")) {
            e.setCancelled(true);
        }
    }
}

