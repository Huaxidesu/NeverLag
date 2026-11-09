/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.plugin.Plugin;

public class AntiInfiniteRail
implements Listener {
    NeverLag plugin;

    public AntiInfiniteRail(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onBlockPhysics(BlockPhysicsEvent e) {
        if (e.getChangedType().equals((Object)Material.RAILS) && (e.getBlock().getType().equals((Object)Material.DETECTOR_RAIL) || e.getBlock().getType().equals((Object)Material.POWERED_RAIL) || e.getBlock().getType().equals((Object)Material.ACTIVATOR_RAIL))) {
            e.setCancelled(true);
        }
    }
}

