/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.Plugin;

public class AntiBot
implements Listener {
    NeverLag plugin;
    private List<String> UserName = new ArrayList<String>();
    private HashMap<String, Integer> IPCache = new HashMap();

    public AntiBot(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                AntiBot.this.clear_run();
            }
        }, (long)NeverLag.IPLoginTime * 20L, (long)NeverLag.IPLoginTime * 20L);
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (!e.getLoginResult().equals((Object)AsyncPlayerPreLoginEvent.Result.ALLOWED)) {
            return;
        }
        if (NeverLag.IPWhiteList.contains(e.getAddress().getHostAddress())) {
            return;
        }
        if (this.IPCache.containsKey(e.getAddress().getHostAddress()) && this.IPCache.get(e.getAddress().getHostAddress()) >= NeverLag.MaxIPLoginNum) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, NeverLag.m_antibot);
            return;
        }
        String username = e.getName().toLowerCase();
        if (this.UserName.contains(username)) {
            return;
        }
        this.UserName.add(username);
        if (!this.IPCache.containsKey(e.getAddress().getHostAddress())) {
            this.IPCache.put(e.getAddress().getHostAddress(), 1);
        } else {
            this.IPCache.put(e.getAddress().getHostAddress(), this.IPCache.get(e.getAddress().getHostAddress()) + 1);
        }
    }

    public void clear_run() {
        this.IPCache.clear();
        this.UserName.clear();
    }
}

