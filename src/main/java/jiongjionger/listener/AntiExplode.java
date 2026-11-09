/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.Plugin;

public class AntiExplode
implements Listener {
    NeverLag plugin;

    public AntiExplode(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onHIGHESTExplosion(EntityExplodeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        e.blockList().clear();
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onMONITORExplosion(EntityExplodeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        e.blockList().clear();
    }
}

