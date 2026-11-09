/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import java.util.Random;
import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

public class SpawnRate
implements Listener {
    NeverLag plugin;

    public SpawnRate(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void AntiRate(CreatureSpawnEvent e) {
        if (e.isCancelled()) {
            return;
        }
        String Reason = e.getSpawnReason().toString().toUpperCase();
        Random r = new Random();
        int rand = r.nextInt(100);
        switch (Reason) {
            case "CHUNK_GEN": {
                if (NeverLag.ChunkRate > 0 && rand <= NeverLag.ChunkRate) break;
                e.setCancelled(true);
                break;
            }
            case "SPAWNER": {
                if (NeverLag.AntiDropExpFromSpawner.booleanValue() || NeverLag.AntiDropFromSpawner.booleanValue()) {
                    e.getEntity().setMetadata("spanwer", (MetadataValue)new FixedMetadataValue((Plugin)this.plugin, (Object)true));
                }
                if (NeverLag.SpawnerRate > 0 && rand <= NeverLag.SpawnerRate) break;
                e.setCancelled(true);
                break;
            }
            case "VILLAGE_DEFENSE": {
                if (NeverLag.IronRate > 0 && rand <= NeverLag.IronRate) break;
                e.setCancelled(true);
                break;
            }
            case "NATURAL": {
                if (NeverLag.CommonRate > 0 && rand <= NeverLag.CommonRate) break;
                e.setCancelled(true);
                break;
            }
            case "NETHER_PORTAL": {
                if (NeverLag.PortalRate > 0 && rand <= NeverLag.PortalRate) break;
                e.setCancelled(true);
                break;
            }
            default: {
                return;
            }
        }
    }

    @EventHandler
    public void onDrop(EntityDeathEvent e) {
        if (e.getEntity().hasMetadata("spanwer")) {
            if (NeverLag.AntiDropExpFromSpawner.booleanValue()) {
                e.setDroppedExp(0);
            }
            if (NeverLag.AntiDropFromSpawner.booleanValue()) {
                e.getDrops().clear();
            }
        }
    }
}

