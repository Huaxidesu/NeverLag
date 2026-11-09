/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.Plugin;

public class AntiCrashChat
implements Listener {
    NeverLag plugin;

    public AntiCrashChat(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void OnChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getMessage() == null) {
            return;
        }
        if (e.getMessage().contains("\u0130")) {
            e.setCancelled(true);
        }
    }
}

