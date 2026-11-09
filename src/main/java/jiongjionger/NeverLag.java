/*
 * Decompiled with CFR 0.152.
 */
package jiongjionger;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ConnectionSide;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.reflect.FieldAccessException;
import com.comphenix.protocol.reflect.StructureModifier;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import jiongjionger.common.MonitorInfoBean;
import jiongjionger.common.MonitorServiceImpl;
import jiongjionger.listener.AntiBigShop;
import jiongjionger.listener.AntiBot;
import jiongjionger.listener.AntiCheatMod;
import jiongjionger.listener.AntiChestBug;
import jiongjionger.listener.AntiCountBug;
import jiongjionger.listener.AntiCrashChat;
import jiongjionger.listener.AntiCrashSign;
import jiongjionger.listener.AntiDoorCheat;
import jiongjionger.listener.AntiExplode;
import jiongjionger.listener.AntiInfiniteRail;
import jiongjionger.listener.AntiMinecraft;
import jiongjionger.listener.AntiMotdPing;
import jiongjionger.listener.AntiNetherHopperCheat;
import jiongjionger.listener.AntiPMM;
import jiongjionger.listener.ChatCommandRate;
import jiongjionger.listener.CheckRedstone;
import jiongjionger.listener.FixPluginBug;
import jiongjionger.listener.MobFarmLimiter;
import jiongjionger.listener.MobLimiter;
import jiongjionger.listener.NoBonemealCheat;
import jiongjionger.listener.NoSpawnChunk;
import jiongjionger.listener.ProtectFarm;
import jiongjionger.listener.SpawnRate;
import jiongjionger.task.ClearEntity;
import jiongjionger.task.ClearItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class NeverLag
extends JavaPlugin
implements Listener {
    private ProtocolManager protocolManager;
    private static final String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
    private static int intversion = 0;
    private static Class<?> aNMSItemstack;
    private static Class<?> aCraftItemstack;
    public static List<String> NoClearItemWorld;
    public static Boolean ClearItem;
    public static int CleanItemTime;
    public static List<Integer> NoClearItemIDList;
    public static Boolean ClearItemFrame;
    public static Boolean Boat;
    public static Boolean ExpBall;
    public static Boolean FallingBlock;
    public static Boolean Painting;
    public static Boolean Minecart;
    public static Boolean Arrow;
    public static Boolean Snowball;
    public static Boolean ClearEntity;
    public static int ClearMobTime;
    public static Boolean ClearLimit;
    public static int ClearLimitNum;
    public static Boolean ClearAnimals;
    public static Boolean ClearMonster;
    public static Boolean ClearSquid;
    public static Boolean ClearVillager;
    public static Boolean CheckRedStone;
    public static int RedstoneLimit;
    public static int RedstoneTime;
    public static List<Integer> RedstoneClearType;
    public static Boolean RedStoneDrop;
    public static Boolean RedStoneMessage;
    public static Boolean IsNoSpawnChunk;
    public static Boolean IsProtectFarm;
    public static Boolean IsAntiBot;
    public static Boolean IsAntiExplode;
    public static int MaxIPLoginNum;
    public static int IPLoginTime;
    public static List<String> IPWhiteList;
    public static Boolean IsAntiFarmMob;
    public static int FarmLimit;
    public static int FarmCleanTime;
    public static Boolean IsMobLimit;
    public static int AnimalNum;
    public static int MonsterNum;
    public static int SpawnerMobChunkNum;
    public static Boolean AntiDoorCheat;
    public static Boolean AntiBonemealCheat;
    public static List<Integer> BonemealBlackList;
    public static Boolean AntiNetherHopperCheat;
    public static Boolean AntiCheatMod;
    public static List<String> AntiCheatModCode;
    public static Boolean AntiMinecraft;
    public static Boolean AntiCountBug;
    public static Boolean SpawnRate;
    public static int ChunkRate;
    public static int SpawnerRate;
    public static int IronRate;
    public static int CommonRate;
    public static int PortalRate;
    public static Boolean AntiDropFromSpawner;
    public static Boolean AntiDropExpFromSpawner;
    public static Boolean ChatCommandRate;
    public static int ChatDelay;
    public static int CommandDelay;
    public static List<String> CommandWhiteList;
    public static Boolean AntiChestBug;
    public static Boolean FixPluginBug;
    public static Boolean AntiMotdPing;
    public static Boolean AntiPMM;
    public static Boolean AntiInvEditor;
    public static Boolean AntiBigShop;
    public static Boolean AntiRail;
    public static Boolean AntiCrashSign;
    public static Boolean AntCrashChat;
    public static String m_chatrate;
    public static String m_commandrate;
    public static String m_redstone;
    public static String m_antibot;
    public static String m_clean_item;
    public static String m_clean_item_pre;
    public static String m_clean_mob;
    public static String m_antichest;
    public static String m_spam;
    public static String m_anti_bigshop;
    public static String m_anti_crashsign;

    static {
        m_redstone = "";
        m_antibot = "";
        m_clean_item = "";
        m_clean_item_pre = "";
        m_clean_mob = "";
        m_antichest = "";
        m_spam = "";
        m_anti_bigshop = "";
        m_anti_crashsign = "";
    }

    public void onEnable() {
        this.LoadConfig();
        Bukkit.getPluginManager().registerEvents((Listener)this, (Plugin)this);
        intversion = Integer.parseInt(Bukkit.getServer().getBukkitVersion().split("-")[0].replace(".", ""));
        if (IsAntiBot.booleanValue()) {
            new AntiBot(this);
        }
        if (IsAntiExplode.booleanValue()) {
            new AntiExplode(this);
        }
        if (CheckRedStone.booleanValue()) {
            new CheckRedstone(this);
        }
        if (IsProtectFarm.booleanValue()) {
            new ProtectFarm(this);
        }
        if (IsAntiFarmMob.booleanValue()) {
            new MobFarmLimiter(this);
        }
        if (IsMobLimit.booleanValue()) {
            new MobLimiter(this);
        }
        if (IsNoSpawnChunk.booleanValue()) {
            new NoSpawnChunk(this);
        }
        if (ClearEntity.booleanValue()) {
            new ClearEntity(this);
        }
        if (ClearItem.booleanValue()) {
            new ClearItem(this);
        }
        if (ChatCommandRate.booleanValue()) {
            new ChatCommandRate(this);
        }
        if (AntiDoorCheat.booleanValue()) {
            new AntiDoorCheat(this);
        }
        if (AntiBonemealCheat.booleanValue() && intversion <= 170) {
            new NoBonemealCheat(this);
        }
        if (AntiNetherHopperCheat.booleanValue()) {
            new AntiNetherHopperCheat(this);
        }
        if (AntiCheatMod.booleanValue()) {
            new AntiCheatMod(this);
        }
        if (AntiMinecraft.booleanValue()) {
            new AntiMinecraft(this);
        }
        if (AntiCountBug.booleanValue()) {
            new AntiCountBug(this);
        }
        if (SpawnRate.booleanValue()) {
            new SpawnRate(this);
        }
        if (AntiChestBug.booleanValue()) {
            new AntiChestBug(this);
        }
        if (FixPluginBug.booleanValue()) {
            new FixPluginBug(this);
        }
        if (AntiMotdPing.booleanValue() && intversion < 183) {
            new AntiMotdPing(this);
        }
        if (AntiPMM.booleanValue() && intversion >= 180) {
            new AntiPMM(this);
        }
        if (AntiBigShop.booleanValue()) {
            new AntiBigShop(this);
        }
        if (AntiCrashSign.booleanValue()) {
            new AntiCrashSign(this);
        }
        if (AntCrashChat.booleanValue()) {
            new AntiCrashChat(this);
        }
        if (AntiRail.booleanValue()) {
            new AntiInfiniteRail(this);
        }
        if (AntiInvEditor.booleanValue()) {
            try {
                this.protocolManager = ProtocolLibrary.getProtocolManager();
            }
            catch (Exception e) {
                this.getServer().getLogger().log(Level.WARNING, "Try to use NeverLag Anti-ALL-U-Want, you must install Protocolib frist!");
                this.getServer().getLogger().log(Level.WARNING, "\u60f3\u8981\u4f7f\u7528NeverLag\u7684\u9632\u5fa1ALL-U-WANT\u7684\u529f\u80fd\uff0c\u4f60\u5fc5\u987b\u5148\u5b89\u88c5\u6b63\u786e\u7248\u672c\u7684Protocolib\uff01");
                return;
            }
            if (this.protocolManager == null) {
                this.getServer().getLogger().log(Level.WARNING, "Try to use NeverLag Anti-ALL-U-Want, you must install Protocolib frist!");
                this.getServer().getLogger().log(Level.WARNING, "\u60f3\u8981\u4f7f\u7528NeverLag\u7684\u9632\u5fa1ALL-U-WANT\u7684\u529f\u80fd\uff0c\u4f60\u5fc5\u987b\u5148\u5b89\u88c5\u6b63\u786e\u7248\u672c\u7684Protocolib\uff01");
                return;
            }
            try {
                aNMSItemstack = Class.forName("net.minecraft.server." + version + ".ItemStack");
                aCraftItemstack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            }
            catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
            this.protocolManager.addPacketListener((PacketListener)new PacketAdapter((Plugin)this, ConnectionSide.CLIENT_SIDE, ListenerPriority.NORMAL, new Integer[]{107}){

                public void onPacketReceiving(PacketEvent e) {
                    if (e.getPlayer().isOp()) {
                        return;
                    }
                    StructureModifier modfier = e.getPacket().getItemModifier();
                    Object nmsStack = null;
                    try {
                        nmsStack = NeverLag.getMethod(aCraftItemstack, "asNMSCopy").invoke((Object)aCraftItemstack, modfier.read(0));
                    }
                    catch (IllegalAccessException illegalAccessException) {
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                    }
                    catch (InvocationTargetException invocationTargetException) {
                    }
                    catch (FieldAccessException fieldAccessException) {
                        // empty catch block
                    }
                    if (nmsStack != null) {
                        try {
                            if (((Boolean)NeverLag.getMethod(aNMSItemstack, "hasTag").invoke(nmsStack, new Object[0])).booleanValue()) {
                                e.setCancelled(true);
                                e.getPlayer().sendMessage(ChatColor.GREEN + "[NeverLag]\u8bf7\u4e0d\u8981\u4f7f\u7528All-U-Want\u7b49\u4f5c\u5f0aMOD\uff01");
                            }
                        }
                        catch (IllegalAccessException illegalAccessException) {
                        }
                        catch (IllegalArgumentException illegalArgumentException) {
                        }
                        catch (InvocationTargetException invocationTargetException) {
                            // empty catch block
                        }
                    }
                }
            });
        }
    }

    public void LoadConfig() {
        if (!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
            this.saveDefaultConfig();
        }
        FileConfiguration config = this.getConfig();
        ClearItem = config.getBoolean("ClearItem.Enable");
        NoClearItemWorld = config.getStringList("ClearItem.NoClearItemWorld");
        CleanItemTime = config.getInt("ClearItem.Delay");
        NoClearItemIDList = config.getIntegerList("ClearItem.NoClearItemIDList");
        ClearItemFrame = config.getBoolean("ClearItem.ItemFrame");
        Boat = config.getBoolean("ClearItem.Boat");
        ExpBall = config.getBoolean("ClearItem.ExpBall");
        FallingBlock = config.getBoolean("ClearItem.FallingBlock");
        Painting = config.getBoolean("ClearItem.Painting");
        Minecart = config.getBoolean("ClearItem.Minecart");
        Arrow = config.getBoolean("ClearItem.Arrow");
        Snowball = config.getBoolean("ClearItem.Snowball");
        ClearEntity = config.getBoolean("ClearEntity.Enable");
        ClearMobTime = config.getInt("ClearEntity.Delay");
        // 原版此处误将 OnlyClearWhenLimit 写入 ClearEntity，导致清理任务被开关值覆盖，
        // 且 ClearLimit 始终为 null。修正为写入独立的阈值开关字段。
        ClearLimit = config.getBoolean("ClearEntity.OnlyClearWhenLimit");
        ClearLimitNum = config.getInt("ClearEntity.Limit");
        ClearAnimals = config.getBoolean("ClearEntity.Animals");
        ClearMonster = config.getBoolean("ClearEntity.Monster");
        ClearSquid = config.getBoolean("ClearEntity.Squid");
        ClearVillager = config.getBoolean("ClearEntity.Villager");
        CheckRedStone = config.getBoolean("RedStone.Enable");
        RedstoneLimit = config.getInt("RedStone.Limit");
        RedstoneTime = config.getInt("RedStone.Time");
        RedstoneClearType = config.getIntegerList("RedStone.Clear");
        RedStoneDrop = config.getBoolean("RedStone.Drop");
        RedStoneMessage = config.getBoolean("RedStone.IsMessage");
        IsNoSpawnChunk = config.getBoolean("Chunk.NoSpawnChunk");
        IsProtectFarm = config.getBoolean("ProtectFarm.Enable");
        IsAntiExplode = config.getBoolean("AntiExplode.Enable");
        IsAntiBot = config.getBoolean("AntiBot.Enable");
        MaxIPLoginNum = config.getInt("AntiBot.Limit");
        IPLoginTime = config.getInt("AntiBot.Time");
        IPWhiteList = config.getStringList("AntiBot.WhiteList");
        IsAntiFarmMob = config.getBoolean("AntiFarmMob.Enable");
        FarmCleanTime = config.getInt("AntiFarmMob.Time");
        FarmLimit = config.getInt("AntiFarmMob.Limit");
        IsMobLimit = config.getBoolean("MobLimit.Enable");
        AnimalNum = config.getInt("MobLimit.Animal");
        MonsterNum = config.getInt("MobLimit.Monster");
        SpawnerMobChunkNum = config.getInt("MobLimit.SpawnerMobChunkNum");
        ChatCommandRate = config.getBoolean("ChatCommandRate.Enable");
        ChatDelay = config.getInt("ChatCommandRate.ChatDelay");
        CommandDelay = config.getInt("ChatCommandRate.CommandDelay");
        CommandWhiteList = config.getStringList("ChatCommandRate.CommandWhiteList");
        AntiDoorCheat = config.getBoolean("AntiDoorCheat.Enable");
        AntiBonemealCheat = config.getBoolean("AntiBonemealCheat.Enable");
        BonemealBlackList = config.getIntegerList("AntiBonemealCheat.BlackList");
        AntiNetherHopperCheat = config.getBoolean("AntiNetherHopperCheat.Enable");
        AntiCheatMod = config.getBoolean("AntiCheatMod.Enable");
        AntiCheatModCode = config.getStringList("AntiCheatMod.Code");
        AntiMinecraft = config.getBoolean("AntiMinecraft.Enable");
        AntiCountBug = config.getBoolean("AntiCountBug.Enable");
        SpawnRate = config.getBoolean("SpawnRate.Enable");
        ChunkRate = config.getInt("SpawnRate.ChunkRate");
        SpawnerRate = config.getInt("SpawnRate.SpawnerRate");
        IronRate = config.getInt("SpawnRate.IronRate");
        CommonRate = config.getInt("SpawnRate.CommonRate");
        PortalRate = config.getInt("SpawnRate.PortalRate");
        AntiDropFromSpawner = config.getBoolean("SpawnRate.AntiDropFromSpawner");
        AntiDropExpFromSpawner = config.getBoolean("SpawnRate.AntiDropExpFromSpawner");
        AntiChestBug = config.getBoolean("AntiChestBug.Enable");
        FixPluginBug = config.getBoolean("FixPluginBug.Enable");
        AntiMotdPing = config.getBoolean("AntiMotdPing.Enable");
        AntiPMM = config.getBoolean("AntiPMM.Enable");
        AntiBigShop = config.getBoolean("FixQuickShop.Enable");
        AntiCrashSign = config.getBoolean("AntiCrashSign.Enable");
        AntCrashChat = config.getBoolean("AntCrashChat.Enable");
        AntiRail = config.getBoolean("AntiRail.Enable");
        AntiInvEditor = config.getBoolean("AntiInvEditor.Enable");
        m_redstone = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("RedStone.Message"));
        m_antibot = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("AntiBot.Message"));
        m_clean_item = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ClearItem.Message"));
        m_clean_item_pre = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ClearItem.PreMessage"));
        m_clean_mob = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ClearEntity.Message"));
        m_commandrate = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ChatCommandRate.CommandMessage"));
        m_chatrate = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ChatCommandRate.ChatMessage"));
        m_antichest = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("AntiChestBug.Message"));
        m_spam = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("ChatCommandRate.Message"));
        m_anti_bigshop = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("FixQuickShop.Message"));
        m_anti_crashsign = ChatColor.translateAlternateColorCodes((char)'&', (String)config.getString("AntiCrashSign.Message"));
    }

    @EventHandler
    public void onBlockFromTo(BlockFromToEvent e) {
        if (e.getToBlock().getType().equals((Object)Material.SKULL)) {
            e.setCancelled(true);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmnd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            return true;
        }
        Player p = (Player)sender;
        if (!p.isOp()) {
            return true;
        }
        if (label.equalsIgnoreCase("NeverLag")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("reload")) {
                    this.LoadConfig();
                    p.sendMessage(ChatColor.GREEN + "[NeverLag]\u914d\u7f6e\u6587\u4ef6\u91cd\u8f7d\u5b8c\u6bd5");
                }
                if (args[0].equalsIgnoreCase("gc")) {
                    long m1 = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    System.gc();
                    System.runFinalization();
                    long l = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    if (l < m1) {
                        p.sendMessage(ChatColor.GREEN + "[NeverLag]\u5f3a\u5236\u5783\u573e\u56de\u6536\u5b8c\u6bd5\uff0c\u603b\u5171\u56de\u6536\u4e86 " + (m1 - l) / 1024L / 1024L + "MB \u5185\u5b58\u3002");
                    } else {
                        p.sendMessage(ChatColor.GREEN + "[NeverLag]\u5f3a\u5236\u5783\u573e\u56de\u6536\u5b8c\u6bd5\uff0c\u4f46\u662f\u6ca1\u6709\u6709\u6548\u7684\u56de\u6536\u5185\u5b58\uff0c\u8bf7\u4e0d\u8981\u9891\u7e41\u4f7f\u7528\u8be5\u529f\u80fd\uff01");
                    }
                }
                if (args[0].equalsIgnoreCase("show")) {
                    MonitorServiceImpl service = new MonitorServiceImpl();
                    MonitorInfoBean monitorInfo = null;
                    try {
                        monitorInfo = service.getMonitorInfoBean();
                    }
                    catch (Exception exception) {
                        exception.printStackTrace();
                    }
                    sender.sendMessage("\u670d\u52a1\u5668\u64cd\u4f5c\u7cfb\u7edf\uff1a" + monitorInfo.getOsName());
                    sender.sendMessage("\u670d\u52a1\u5668\u603bCPU\u5360\u7528\u7387\uff1a" + monitorInfo.getCpuRatio());
                    sender.sendMessage("\u670d\u52a1\u5668\u8fdb\u7a0b\u6570\u91cf\uff1a" + monitorInfo.getTotalThread());
                    sender.sendMessage("\u5f53\u524d\u7a0b\u5e8f\u6700\u5927\u53ef\u7528\u5185\u5b58\uff1a" + monitorInfo.getMaxMemory());
                    sender.sendMessage("\u5f53\u524d\u7a0b\u5e8f\u76ee\u524d\u5206\u914d\u5185\u5b58\uff1a" + monitorInfo.getTotalMemory());
                    sender.sendMessage("\u5f53\u524d\u7a0b\u5e8f\u76ee\u524d\u5269\u4f59\u5185\u5b58\uff1a" + monitorInfo.getFreeMemory());
                    sender.sendMessage("\u670d\u52a1\u5668\u7269\u7406\u5185\u5b58\u603b\u91cf\uff1a" + monitorInfo.getTotalMemorySize());
                    sender.sendMessage("\u670d\u52a1\u5668\u7269\u7406\u5185\u5b58\u5df2\u4f7f\u7528\uff1a" + monitorInfo.getUsedMemory());
                    sender.sendMessage("\u670d\u52a1\u5668\u7269\u7406\u5185\u5b58\u5269\u4f59\uff1a" + monitorInfo.getFreePhysicalMemorySize());
                    sender.sendMessage("\u5f53\u524d\u8fd0\u884c\u73af\u5883PID\uff1a " + service.getPid());
                    sender.sendMessage("\u5f53\u524d\u8fd0\u884c\u73af\u5883\u5269\u4f59\u5185\u5b58\uff1a " + service.getFreeMemoryMB());
                    sender.sendMessage("\u5f53\u524d\u8fd0\u884c\u73af\u5883\u4f7f\u7528\u5185\u5b58\uff1a" + service.getUsedMemoryMB());
                }
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("find")) {
                String send_namelist;
                if (args[1].equalsIgnoreCase("display")) {
                    ArrayList<String> name_list = new ArrayList<String>();
                    for (Player online_player : Bukkit.getOnlinePlayers()) {
                        for (ItemStack item : online_player.getInventory()) {
                            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains(args[2]) || name_list.contains(online_player.getName())) continue;
                            name_list.add(online_player.getName());
                        }
                    }
                    send_namelist = "";
                    for (String string : name_list) {
                        send_namelist = String.valueOf(send_namelist) + string + " ";
                    }
                    p.sendMessage(send_namelist);
                    name_list.clear();
                } else if (args[1].equalsIgnoreCase("lore")) {
                    ArrayList<String> name_list = new ArrayList<String>();
                    for (Player online_player : Bukkit.getOnlinePlayers()) {
                        for (ItemStack item : online_player.getInventory()) {
                            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) continue;
                            for (String lore : item.getItemMeta().getLore()) {
                                if (!lore.contains(args[2]) || name_list.contains(online_player.getName())) continue;
                                name_list.add(online_player.getName());
                            }
                        }
                    }
                    send_namelist = "";
                    for (String string : name_list) {
                        send_namelist = String.valueOf(send_namelist) + string + " ";
                    }
                    p.sendMessage(send_namelist);
                    name_list.clear();
                }
            }
        }
        return true;
    }

    public static Method getMethod(Class<?> cl, String method) {
        Method[] methodArray = cl.getMethods();
        int n = methodArray.length;
        int n2 = 0;
        while (n2 < n) {
            Method m = methodArray[n2];
            if (m.getName().equals(method)) {
                return m;
            }
            ++n2;
        }
        return null;
    }
}

