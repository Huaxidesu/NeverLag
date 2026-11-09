/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import java.util.HashMap;
import java.util.Iterator;
import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.plugin.Plugin;

public class CheckRedstone
implements Listener {
    NeverLag plugin;
    private HashMap<Location, Integer> cache = new HashMap();

    public CheckRedstone(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                CheckRedstone.this.redstone_run();
            }
        }, 0L, (long)NeverLag.RedstoneTime * 20L);
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false)
    public void onBlockRedstone(BlockRedstoneEvent e) {
        if (!NeverLag.RedstoneClearType.contains(e.getBlock().getTypeId())) {
            return;
        }
        if (this.cache.containsKey(e.getBlock().getLocation())) {
            this.cache.put(e.getBlock().getLocation(), this.cache.get(e.getBlock().getLocation()) + 1);
        } else {
            this.cache.put(e.getBlock().getLocation(), 1);
        }
    }

    public void redstone_run() {
        if (!this.cache.isEmpty()) {
            Boolean flag = false;
            Location location = null;
            for (Location loc : this.cache.keySet()) {
                if (this.cache.get(loc) >= NeverLag.RedstoneLimit) {
                    Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, (Runnable)new restone_drop(loc), 2L);
                    if (location == null) {
                        location = loc;
                    }
                    if (!flag.booleanValue()) {
                        flag = true;
                    }
                }
                loc = null;
            }
            if (flag.booleanValue() && NeverLag.RedStoneMessage.booleanValue()) {
                if (location == null) {
                    Bukkit.getServer().broadcastMessage(NeverLag.m_redstone);
                } else {
                    String NameList = "";
                    int count = 0;
                    Entity[] entityArray = location.getChunk().getEntities();
                    int n = entityArray.length;
                    int n2 = 0;
                    while (n2 < n) {
                        Player p;
                        Entity entity = entityArray[n2];
                        if (count >= 3) break;
                        if (entity instanceof Player && !(p = (Player)entity).hasMetadata("NPC")) {
                            NameList = NameList == "" ? p.getName() : String.valueOf(NameList) + ", " + p.getName();
                            ++count;
                        }
                        ++n2;
                    }
                    count = 0;
                    Bukkit.getServer().broadcastMessage(NeverLag.m_redstone.replace("%Location%", "[" + location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + "]").replace("%Player%", NameList));
                    NameList = "";
                }
            }
            Iterator<Location> iterator = null;
            this.cache.clear();
        }
    }

    private class restone_drop
    implements Runnable {
        Location loc;

        public restone_drop(Location loc) {
            this.loc = loc;
        }

        @Override
        public void run() {
            try {
                if (NeverLag.RedStoneDrop.booleanValue()) {
                    this.loc.getBlock().breakNaturally();
                } else {
                    this.loc.getBlock().setType(Material.AIR);
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }
}

