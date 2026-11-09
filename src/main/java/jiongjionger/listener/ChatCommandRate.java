/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import java.util.HashMap;
import jiongjionger.NeverLag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class ChatCommandRate
implements Listener {
    NeverLag plugin;
    private HashMap<String, Long> CommandCoolDown = new HashMap();
    private HashMap<String, Long> ChatCoolDown = new HashMap();

    public ChatCommandRate(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask((Plugin)plg, new Runnable(){

            @Override
            public void run() {
                ChatCommandRate.this.CacheClear();
            }
        }, 0L, 72000L);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void OnCommand(PlayerCommandPreprocessEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getPlayer().isOp()) {
            return;
        }
        if (e.getPlayer().hasPermission("ChatCommandRate.Pass")) {
            return;
        }
        String message = e.getMessage().toLowerCase();
        if ((message = message.replace(" ", "").replace("\u3002", ".").replace("\uff1a", ":")).contains("com:") || message.contains("net:") || message.contains("cn:") || message.contains("cc:") || message.contains("tk:") || message.contains("org:")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(NeverLag.m_spam);
            return;
        }
        String[] command = e.getMessage().toLowerCase().split(" ");
        if (command.length >= 1 && NeverLag.CommandWhiteList.contains(command[0])) {
            command = null;
            return;
        }
        command = null;
        if (this.CommandCoolDown.containsKey(e.getPlayer().getName())) {
            if (this.CommandCoolDown.get(e.getPlayer().getName()) > System.currentTimeMillis() / 1000L) {
                e.getPlayer().sendMessage(NeverLag.m_commandrate);
                e.setCancelled(true);
            } else {
                long time = System.currentTimeMillis() / 1000L + (long)NeverLag.CommandDelay;
                this.CommandCoolDown.put(e.getPlayer().getName(), time);
            }
        } else {
            long time = System.currentTimeMillis() / 1000L + (long)NeverLag.CommandDelay;
            this.CommandCoolDown.put(e.getPlayer().getName(), time);
        }
    }

    @EventHandler
    public void OnChat(AsyncPlayerChatEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if (e.getPlayer().isOp()) {
            return;
        }
        if (e.getPlayer().hasPermission("ChatCommandRate.Pass")) {
            return;
        }
        String message = e.getMessage().toLowerCase();
        if ((message = message.replace(" ", "").replace("\u3002", ".").replace("\uff1a", ":")).contains("com:") || message.contains("net:") || message.contains("cn:") || message.contains("cc:") || message.contains("tk:") || message.contains("org:")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(NeverLag.m_spam);
            return;
        }
        if (this.ChatCoolDown.containsKey(e.getPlayer().getName())) {
            if (this.ChatCoolDown.get(e.getPlayer().getName()) > System.currentTimeMillis() / 1000L) {
                e.getPlayer().sendMessage(NeverLag.m_chatrate);
                e.setCancelled(true);
            } else {
                long time = System.currentTimeMillis() / 1000L + (long)NeverLag.ChatDelay;
                this.ChatCoolDown.put(e.getPlayer().getName(), time);
            }
        } else {
            long time = System.currentTimeMillis() / 1000L + (long)NeverLag.ChatDelay;
            this.ChatCoolDown.put(e.getPlayer().getName(), time);
        }
    }

    @EventHandler
    public void OnQuit(PlayerQuitEvent e) {
        if (this.ChatCoolDown.containsKey(e.getPlayer().getName())) {
            this.ChatCoolDown.remove(e.getPlayer().getName());
        }
        if (this.ChatCoolDown.containsKey(e.getPlayer().getName())) {
            this.ChatCoolDown.remove(e.getPlayer().getName());
        }
    }

    public void CacheClear() {
        this.CommandCoolDown.clear();
        this.ChatCoolDown.clear();
    }
}

