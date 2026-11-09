/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.task;

import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Snowball;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class ClearItem
implements Listener {
    NeverLag plugin;
    public int nowtime = 0;

    public ClearItem(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                ClearItem.this.clean_run();
            }
        }, (long)NeverLag.CleanItemTime * 20L, (long)NeverLag.CleanItemTime * 20L);
        if (NeverLag.CleanItemTime > 90) {
            plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

                @Override
                public void run() {
                    ClearItem.this.domessage();
                }
            }, 20L, 20L);
        }
    }

    public void domessage() {
        ++this.nowtime;
        if (NeverLag.CleanItemTime - this.nowtime == 60) {
            Bukkit.getServer().broadcastMessage(NeverLag.m_clean_item_pre.replace("%TIME%", "60"));
        }
        if (NeverLag.CleanItemTime - this.nowtime == 30) {
            Bukkit.getServer().broadcastMessage(NeverLag.m_clean_item_pre.replace("%TIME%", "30"));
        }
        if (NeverLag.CleanItemTime - this.nowtime == 10) {
            Bukkit.getServer().broadcastMessage(NeverLag.m_clean_item_pre.replace("%TIME%", "10"));
        }
        if (NeverLag.CleanItemTime - this.nowtime <= 0) {
            this.nowtime = 0;
        }
    }

    public void clean_run() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            if (NeverLag.NoClearItemWorld.contains(world.getName())) continue;
            for (Entity ent : world.getEntities()) {
                if (ent instanceof Item && NeverLag.ClearItem.booleanValue()) {
                    Item item = (Item)ent;
                    if (!NeverLag.NoClearItemIDList.contains(item.getItemStack().getTypeId())) {
                        ++count;
                        ent.remove();
                    }
                    item = null;
                    continue;
                }
                if (ent instanceof ItemFrame && NeverLag.ClearItemFrame.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof Boat && NeverLag.Boat.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof ExperienceOrb && NeverLag.ExpBall.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof FallingBlock && NeverLag.FallingBlock.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof Painting && NeverLag.Painting.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof Minecart && NeverLag.Minecart.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (ent instanceof Arrow && NeverLag.Arrow.booleanValue()) {
                    ++count;
                    ent.remove();
                    continue;
                }
                if (!(ent instanceof Snowball) || !NeverLag.Snowball.booleanValue()) continue;
                ++count;
                ent.remove();
            }
        }
        Bukkit.getServer().broadcastMessage(NeverLag.m_clean_item.replace("%COUNT%", String.valueOf(count)));
        count = 0;
    }
}

