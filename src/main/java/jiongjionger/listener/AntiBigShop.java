/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger.listener;

import jiongjionger.NeverLag;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.Plugin;

public class AntiBigShop
implements Listener {
    NeverLag plugin;

    public AntiBigShop(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)plg);
    }

    @EventHandler
    public void OnPlaceChest(BlockPlaceEvent e) {
        if (e.isCancelled()) {
            return;
        }
        if ((e.getBlock().getType().equals((Object)Material.CHEST) || e.getBlock().getType().equals((Object)Material.TRAPPED_CHEST)) && this.CheckShop(e.getBlock()).booleanValue()) {
            e.getPlayer().sendMessage(NeverLag.m_anti_bigshop);
            e.setCancelled(true);
        }
    }

    private Boolean CheckShop(Block b) {
        String content;
        int n;
        int n2;
        String[] stringArray;
        Sign sign;
        if (b == null) {
            return false;
        }
        Block nearchest = null;
        if (b.getType().equals((Object)Material.CHEST)) {
            nearchest = this.GetNearChest(b);
        } else if (b.getType().equals((Object)Material.TRAPPED_CHEST)) {
            nearchest = this.GetNearTrappedChest(b);
        }
        if (nearchest == null) {
            return false;
        }
        if (nearchest.getRelative(BlockFace.EAST).getType().equals((Object)Material.WALL_SIGN)) {
            sign = (Sign)nearchest.getRelative(BlockFace.EAST).getState();
            stringArray = sign.getLines();
            n2 = stringArray.length;
            n = 0;
            while (n < n2) {
                content = stringArray[n];
                if (content.toLowerCase().contains("quickshop")) {
                    return true;
                }
                ++n;
            }
        }
        if (nearchest.getRelative(BlockFace.NORTH).getType().equals((Object)Material.WALL_SIGN)) {
            sign = (Sign)nearchest.getRelative(BlockFace.NORTH).getState();
            stringArray = sign.getLines();
            n2 = stringArray.length;
            n = 0;
            while (n < n2) {
                content = stringArray[n];
                if (content.toLowerCase().contains("quickshop")) {
                    return true;
                }
                ++n;
            }
        }
        if (nearchest.getRelative(BlockFace.SOUTH).getType().equals((Object)Material.WALL_SIGN)) {
            sign = (Sign)nearchest.getRelative(BlockFace.SOUTH).getState();
            stringArray = sign.getLines();
            n2 = stringArray.length;
            n = 0;
            while (n < n2) {
                content = stringArray[n];
                if (content.toLowerCase().contains("quickshop")) {
                    return true;
                }
                ++n;
            }
        }
        if (nearchest.getRelative(BlockFace.WEST).getType().equals((Object)Material.WALL_SIGN)) {
            sign = (Sign)nearchest.getRelative(BlockFace.WEST).getState();
            stringArray = sign.getLines();
            n2 = stringArray.length;
            n = 0;
            while (n < n2) {
                content = stringArray[n];
                if (content.toLowerCase().contains("quickshop")) {
                    return true;
                }
                ++n;
            }
        }
        return false;
    }

    private Block GetNearChest(Block b) {
        if (b.getRelative(BlockFace.EAST).getType().equals((Object)Material.CHEST)) {
            return b.getRelative(BlockFace.EAST);
        }
        if (b.getRelative(BlockFace.NORTH).getType().equals((Object)Material.CHEST)) {
            return b.getRelative(BlockFace.NORTH);
        }
        if (b.getRelative(BlockFace.SOUTH).getType().equals((Object)Material.CHEST)) {
            return b.getRelative(BlockFace.SOUTH);
        }
        if (b.getRelative(BlockFace.WEST).getType().equals((Object)Material.CHEST)) {
            return b.getRelative(BlockFace.WEST);
        }
        return null;
    }

    private Block GetNearTrappedChest(Block b) {
        if (b.getRelative(BlockFace.EAST).getType().equals((Object)Material.TRAPPED_CHEST)) {
            return b.getRelative(BlockFace.EAST);
        }
        if (b.getRelative(BlockFace.NORTH).getType().equals((Object)Material.TRAPPED_CHEST)) {
            return b.getRelative(BlockFace.NORTH);
        }
        if (b.getRelative(BlockFace.SOUTH).getType().equals((Object)Material.TRAPPED_CHEST)) {
            return b.getRelative(BlockFace.SOUTH);
        }
        if (b.getRelative(BlockFace.WEST).getType().equals((Object)Material.TRAPPED_CHEST)) {
            return b.getRelative(BlockFace.WEST);
        }
        return null;
    }
}

