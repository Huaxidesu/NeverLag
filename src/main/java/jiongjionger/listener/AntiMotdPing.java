/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import java.util.HashMap;
import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.Plugin;

public class AntiMotdPing
implements Listener {
    NeverLag plugin;
    private HashMap<String, Long> log = new HashMap();

    public AntiMotdPing(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                AntiMotdPing.this.clear_run();
            }
        }, 2400L, 2400L);
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (this.log.containsKey(e.getAddress().getHostAddress())) {
            if (this.log.get(e.getAddress().getHostAddress()) + 100L > System.currentTimeMillis()) {
                e.setMotd(null);
            }
        } else {
            this.log.put(e.getAddress().getHostAddress(), System.currentTimeMillis());
        }
    }

    public void clear_run() {
        this.log.clear();
    }
}

