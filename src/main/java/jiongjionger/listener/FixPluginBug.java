/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;

public class FixPluginBug
implements Listener {
    NeverLag plugin;

    public FixPluginBug(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onSign(SignChangeEvent e) {
        if (e.isCancelled()) {
            return;
        }
        String[] stringArray = e.getLines();
        int n = stringArray.length;
        int n2 = 0;
        while (n2 < n) {
            String line = stringArray[n2];
            if (line.contains("/") || line.toLowerCase().contains("[command]")) {
                if (!e.getPlayer().isOp() && !e.isCancelled()) {
                    e.setCancelled(true);
                    e.getBlock().breakNaturally();
                }
            } else if ((line.toUpperCase().contains("B1E1") || line.toUpperCase().contains("1E1B")) && !e.isCancelled()) {
                e.setCancelled(true);
                e.getBlock().breakNaturally();
            }
            ++n2;
        }
    }
}

