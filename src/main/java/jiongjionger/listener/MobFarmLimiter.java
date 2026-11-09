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
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.Plugin;

public class MobFarmLimiter
implements Listener {
    NeverLag plugin;

    public MobFarmLimiter(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                MobFarmLimiter.this.CleanFarm();
            }
        }, 0L, (long)NeverLag.FarmCleanTime * 20L);
    }

    @EventHandler
    public void onChunkLoadFarmLimiter(ChunkLoadEvent e) {
        if (e.getChunk().getEntities().length < NeverLag.FarmLimit) {
            return;
        }
        Entity[] entityArray = e.getChunk().getEntities();
        int n = entityArray.length;
        int n2 = 0;
        while (n2 < n) {
            Entity entity = entityArray[n2];
            if ((entity instanceof Monster || entity instanceof Animals || entity instanceof Villager || entity.getType() == EntityType.SQUID) && entity.getNearbyEntities(0.5, 3.0, 0.5).size() >= NeverLag.FarmLimit && !entity.hasMetadata("NPC") && !entity.hasMetadata("MyPet")) {
                entity.remove();
            }
            ++n2;
        }
    }

    public void CleanFarm() {
        for (World w : Bukkit.getWorlds()) {
            int count = w.getLivingEntities().size();
            if (count < NeverLag.FarmLimit) {
                return;
            }
            for (LivingEntity entity : w.getLivingEntities()) {
                if (!(entity instanceof Monster) && !(entity instanceof Animals) && !(entity instanceof Villager) && entity.getType() != EntityType.SQUID || entity.getNearbyEntities(0.5, 3.0, 0.5).size() < NeverLag.FarmLimit || entity.hasMetadata("NPC") || entity.hasMetadata("MyPet")) continue;
                entity.remove();
            }
        }
    }
}

