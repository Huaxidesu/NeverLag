/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.plugin.Plugin;

public class AntiPMM
implements Listener {
    NeverLag plugin;

    public AntiPMM(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent e) {
        if (e.isCancelled()) {
            return;
        }
        for (Block b : e.getBlocks()) {
            // Material.SLIME_BLOCK 在 1.7.10 的 Bukkit API 中不存在，直接引用会在运行时
            // 抛出 NoSuchFieldError。按名称比较，1.7.10 上自然不会命中。
            if (!"SLIME_BLOCK".equals(b.getType().name())) continue;
            e.setCancelled(true);
            return;
        }
    }
}

