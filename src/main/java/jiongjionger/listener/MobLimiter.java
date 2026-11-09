/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

public class MobLimiter
implements Listener {
    private int cache_count = 0;
    private int cache_time = 0;
    NeverLag plugin;

    public MobLimiter(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        int count;
        if (e.isCancelled()) {
            return;
        }
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPAWNER) {
            if (this.cache_time == 0 || this.cache_time >= 40) {
                count = 0;
                for (World w : Bukkit.getWorlds()) {
                    for (LivingEntity entity : w.getLivingEntities()) {
                        if (!(entity instanceof Monster) && !(entity instanceof Animals) && !(entity instanceof Villager) && entity.getType() != EntityType.SQUID && entity.getType() != EntityType.BAT) continue;
                        ++count;
                    }
                }
                this.cache_count = count;
                this.cache_time = 0;
                count = 0;
            }
            ++this.cache_time;
            if (e.getEntity() instanceof Animals) {
                if (this.cache_count >= NeverLag.AnimalNum) {
                    e.setCancelled(true);
                }
            } else if (e.getEntity() instanceof Monster && this.cache_count >= NeverLag.MonsterNum) {
                e.setCancelled(true);
            }
        }
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER) {
            count = 0;
            Entity[] entityArray = e.getLocation().getChunk().getEntities();
            int n = entityArray.length;
            int n2 = 0;
            while (n2 < n) {
                Entity entity = entityArray[n2];
                if (entity instanceof Monster) {
                    ++count;
                }
                ++n2;
            }
            if (count >= NeverLag.SpawnerMobChunkNum) {
                e.setCancelled(true);
            }
            boolean bl = false;
        }
    }
}

