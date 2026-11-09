/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.plugin.Plugin;

public class AntiDoorCheat
implements Listener {
    NeverLag plugin;

    public AntiDoorCheat(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (e.isCancelled() && (e.getItemInHand().getType() == Material.WOOD_DOOR || e.getItemInHand().getType() == Material.IRON_DOOR)) {
            Entity[] entityArray = e.getPlayer().getLocation().getChunk().getEntities();
            int n = entityArray.length;
            int n2 = 0;
            while (n2 < n) {
                Entity entity = entityArray[n2];
                if (entity instanceof Item && (((Item)entity).getItemStack().getType() == Material.SUGAR_CANE || ((Item)entity).getItemStack().getType() == Material.CACTUS)) {
                    entity.remove();
                }
                ++n2;
            }
        }
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        if (e.isCancelled() || e.getPlayer() == null) {
            return;
        }
        if (!(e.getPlayer().getItemInHand().getType() != Material.WOOD_DOOR && e.getPlayer().getItemInHand().getType() != Material.IRON_DOOR || e.getItem().getItemStack().getType() != Material.SUGAR_CANE && e.getItem().getItemStack().getType() != Material.CACTUS)) {
            e.setCancelled(true);
        }
    }
}

