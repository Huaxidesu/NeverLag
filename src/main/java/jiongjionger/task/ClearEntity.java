/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.task;

import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Villager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ClearEntity
implements Listener {
    NeverLag plugin;

    public ClearEntity(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        // Delay 单位为秒；非正值会使调度周期为 0 而导致插件启动失败，此处设下限 1 秒。
        long intervalTicks = Math.max(1L, (long)NeverLag.ClearMobTime) * 20L;
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                ClearEntity.this.clean_run();
            }
        }, intervalTicks, intervalTicks);
    }

    public void clean_run() {
        // 原版无条件按总量阈值提前返回，忽略 OnlyClearWhenLimit 开关。
        // 仅在开启「仅到达数量上限才清理」时才执行阈值判断。
        if (Boolean.TRUE.equals(NeverLag.ClearLimit)) {
            int num = 0;
            for (World world : Bukkit.getWorlds()) {
                num += world.getLivingEntities().size();
            }
            if (num < NeverLag.ClearLimitNum) {
                return;
            }
        }
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity env : world.getLivingEntities()) {
                if (env instanceof Animals && NeverLag.ClearAnimals.booleanValue()) {
                    if (env.hasMetadata("NPC") || env.hasMetadata("MyPet")) continue;
                    env.remove();
                    ++count;
                    continue;
                }
                if (env instanceof Monster && NeverLag.ClearMonster.booleanValue()) {
                    if (env.hasMetadata("NPC") || env.hasMetadata("MyPet")) continue;
                    env.remove();
                    ++count;
                    continue;
                }
                if (env instanceof Squid && NeverLag.ClearSquid.booleanValue()) {
                    if (env.hasMetadata("NPC") || env.hasMetadata("MyPet")) continue;
                    env.remove();
                    ++count;
                    continue;
                }
                if (!(env instanceof Villager) || !NeverLag.ClearVillager.booleanValue() || env.hasMetadata("NPC") || env.hasMetadata("MyPet")) continue;
                env.remove();
                ++count;
            }
        }
        Bukkit.getServer().broadcastMessage(NeverLag.m_clean_mob.replace("%COUNT%", String.valueOf(count)));
        count = 0;
    }
}

