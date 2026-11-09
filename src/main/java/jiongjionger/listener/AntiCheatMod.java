/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

public class AntiCheatMod
implements Listener {
    NeverLag plugin;
    private String username = null;

    public AntiCheatMod(NeverLag plg) {
        this.plugin = plg;
        this.username = null;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent e) {
        if (!e.getPlayer().hasPermission("NeverLag.NoCheck")) {
            this.username = e.getPlayer().getName();
            Bukkit.getServer().getScheduler().runTaskLaterAsynchronously((Plugin)this.plugin, new Runnable(){

                @Override
                public void run() {
                    AntiCheatMod.this.AsyncSendTask();
                }
            }, 0L);
        }
    }

    public void AsyncSendTask() {
        if (this.username == "" || this.username == null) {
            return;
        }
        String name = this.username;
        this.username = null;
        Player p = Bukkit.getPlayer((String)name);
        if (p == null) {
            return;
        }
        for (String code : NeverLag.AntiCheatModCode) {
            if (p.isOnline()) continue;
            p.sendMessage(ChatColor.translateAlternateColorCodes((char)'&', (String)code));
        }
    }
}

