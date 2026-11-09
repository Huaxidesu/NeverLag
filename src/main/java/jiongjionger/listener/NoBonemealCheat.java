/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;

public class NoBonemealCheat
implements Listener {
    NeverLag plugin;

    public NoBonemealCheat(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void AntiCheat(PlayerInteractEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (!e.getPlayer().getItemInHand().getType().equals((Object)Material.INK_SACK)) {
                return;
            }
            if (NeverLag.BonemealBlackList.contains(e.getClickedBlock().getTypeId())) {
                e.setCancelled(true);
            }
        }
    }
}

