package jiongjionger.listener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;

/**
 * 高频红石检测：统计检测周期内同一位置的红石变化次数，超过阀值后没收红石，
 * 并可按配置在该位置生成一次爆炸。
 *
 * 原版红石线、红石块、红石火把的变化由 BlockRedstoneEvent 提供，按配置的物品 ID 过滤。
 * GTNH 的 ProjectRed Transmission 线缆属于 ForgeMultipart 部件，所在方块是多方块代理
 * 方块，不会触发 BlockRedstoneEvent，原版过滤条件无法命中。ProjectRed 每次信号传播结束
 * 都会对每个发生变化的邻居坐标调用 World.notifyNeighborsOfStateExcept（SRG 名
 * func_147460_e），该调用会派发 BlockPhysicsEvent，且 getChangedType 为多方块代理方块。
 * 因此以该事件作为线缆的统计入口，并把计数归到真正承载线缆的方块坐标上，避免计数被
 * 邻居坐标稀释。
 *
 * 检测与没收都在主线程执行；反射得到的类与方法在构造时解析一次，未安装对应模组时
 * 相应功能静默停用。
 */
public class CheckRedstone
implements Listener {

    /** 由本插件发起的爆炸标志。AntiExplode 会清空爆炸的方块列表，需要据此放行。 */
    public static volatile boolean pluginExplosion = false;

    private static final String CRAFT_PACKAGE = "v1_7_R4";
    private static final String[] WIRE_CLASS_NAMES = {
        "mrtjp.projectred.transmission.WirePart",
        "mrtjp.projectred.transmission.FramedWirePart"
    };
    /** ProjectRed 逻辑门。门在每次输出变化时也会向周围发邻居更新，同样可以制造高频。 */
    private static final String[] GATE_CLASS_NAMES = {
        "mrtjp.projectred.integration.GatePart"
    };
    private final NeverLag plugin;
    /** 原版红石的计数，键为方块坐标。 */
    private final HashMap<Location, Integer> cache = new HashMap<Location, Integer>();
    /** ProjectRed 线缆所在方块坐标的计数。 */
    private final HashMap<Location, Integer> partCache = new HashMap<Location, Integer>();
    /** 按区块合并的线缆计数，键为 世界:区块X:区块Z。 */
    private final HashMap<String, Integer> chunkCache = new HashMap<String, Integer>();
    /** 区块内的代表坐标，合计超限但逐个坐标都未超限时用其定位。 */
    private final HashMap<String, Location> chunkSample = new HashMap<String, Location>();
    /** ProjectRed 逻辑门所在方块坐标的计数。 */
    private final HashMap<Location, Integer> gateCache = new HashMap<Location, Integer>();
    /** 按区块合并的逻辑门计数，键为 世界:区块X:区块Z。 */
    private final HashMap<String, Integer> gateChunkCache = new HashMap<String, Integer>();
    /** 逻辑门的区块代表坐标。 */
    private final HashMap<String, Location> gateChunkSample = new HashMap<String, Location>();
    /** 本轮检测周期内已生成的爆炸次数，用于限制连续爆炸。 */
    private int explosionCount;

    private final Class<?> tileClass;
    private final Class<?> partClass;
    private final List<Class<?>> wireClasses = new ArrayList<Class<?>>();
    private final List<Class<?>> gateClasses = new ArrayList<Class<?>>();
    private final int proxyBlockId;
    private final Method tileEntityAt;
    private final Method remPart;

    public CheckRedstone(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents(this, plg);
        plg.getServer().getScheduler().scheduleSyncRepeatingTask(plg, new Runnable(){

            @Override
            public void run() {
                CheckRedstone.this.redstone_run();
            }
        }, 0L, (long)NeverLag.RedstoneTime * 20L);

        this.tileClass = resolveClass("codechicken.multipart.TileMultipart");
        this.partClass = resolveClass("codechicken.multipart.TMultiPart");
        this.proxyBlockId = this.tileClass == null ? -1 : resolveProxyBlockId();
        this.tileEntityAt = resolveTileEntityAt();
        this.remPart = this.tileClass == null || this.partClass == null ? null : resolveMethod(this.tileClass, "remPart", this.partClass);
        for (int i = 0; i < WIRE_CLASS_NAMES.length; ++i) {
            Class<?> wireClass = resolveClass(WIRE_CLASS_NAMES[i]);
            if (wireClass != null) {
                this.wireClasses.add(wireClass);
            }
        }
        for (int i = 0; i < GATE_CLASS_NAMES.length; ++i) {
            Class<?> gateClass = resolveClass(GATE_CLASS_NAMES[i]);
            if (gateClass != null) {
                this.gateClasses.add(gateClass);
            }
        }
        if (!NeverLag.ProjectRedEnable.booleanValue()) {
            plg.getLogger().info("[NeverLag] ProjectRed 线缆高频检测未启用");
        } else if (this.proxyBlockId > 0 && !this.wireClasses.isEmpty() && this.remPart != null) {
            plg.getLogger().info("[NeverLag] 已启用 ProjectRed 线缆高频检测，多方块代理方块 ID " + this.proxyBlockId);
        } else {
            plg.getLogger().info("[NeverLag] 未找到 ProjectRed Transmission 或 ForgeMultipart，线缆高频检测不生效");
        }
        if (!NeverLag.GateEnable.booleanValue()) {
            plg.getLogger().info("[NeverLag] ProjectRed 逻辑门高频检测未启用");
        } else if (this.proxyBlockId > 0 && !this.gateClasses.isEmpty() && this.remPart != null) {
            plg.getLogger().info("[NeverLag] 已启用 ProjectRed 逻辑门高频检测，多方块代理方块 ID " + this.proxyBlockId);
        } else {
            plg.getLogger().info("[NeverLag] 未找到 ProjectRed 逻辑门或 ForgeMultipart，逻辑门高频检测不生效");
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false)
    public void onBlockRedstone(BlockRedstoneEvent e) {
        if (!NeverLag.RedstoneClearType.contains(e.getBlock().getTypeId())) {
            return;
        }
        bump(this.cache, e.getBlock().getLocation());
    }

    /**
     * 多方块邻居更新。先按方块 ID 快速排除与线缆无关的更新，再定位承载线缆的坐标。
     */
    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=false)
    public void onBlockPhysics(BlockPhysicsEvent e) {
        if (this.proxyBlockId <= 0) {
            return;
        }
        boolean detectWire = NeverLag.ProjectRedEnable.booleanValue();
        boolean detectGate = NeverLag.GateEnable.booleanValue();
        if (!detectWire && !detectGate) {
            return;
        }
        if (e.getChangedTypeId() != this.proxyBlockId) {
            return;
        }
        Block source = e.getBlock();
        if (detectWire) {
            Location tileLoc = this.findPartTile(source, this.wireClasses);
            if (tileLoc != null) {
                bump(this.partCache, tileLoc);
                String key = chunkKey(tileLoc);
                bump(this.chunkCache, key);
                if (!this.chunkSample.containsKey(key)) {
                    this.chunkSample.put(key, tileLoc);
                }
            }
        }
        if (detectGate) {
            Location gateLoc = this.findPartTile(source, this.gateClasses);
            if (gateLoc != null) {
                bump(this.gateCache, gateLoc);
                String key = chunkKey(gateLoc);
                bump(this.gateChunkCache, key);
                if (!this.gateChunkSample.containsKey(key)) {
                    this.gateChunkSample.put(key, gateLoc);
                }
            }
        }
    }

    public void redstone_run() {
        this.explosionCount = 0;
        ArrayList<Location> targets = new ArrayList<Location>();
        ArrayList<Location> wireTargets = new ArrayList<Location>();
        ArrayList<Location> gateTargets = new ArrayList<Location>();
        if (!this.cache.isEmpty()) {
            for (Map.Entry<Location, Integer> entry : this.cache.entrySet()) {
                if (entry.getValue() >= NeverLag.RedstoneLimit) {
                    targets.add(entry.getKey());
                }
            }
            this.cache.clear();
        }
        if (!this.partCache.isEmpty()) {
            this.collectTargets(this.partCache, this.chunkCache, this.chunkSample, NeverLag.ProjectRedLimit, NeverLag.ProjectRedChunkLimit, wireTargets);
            this.partCache.clear();
            this.chunkCache.clear();
            this.chunkSample.clear();
        }
        if (!this.gateCache.isEmpty()) {
            this.collectTargets(this.gateCache, this.gateChunkCache, this.gateChunkSample, NeverLag.GateLimit, NeverLag.GateChunkLimit, gateTargets);
            this.gateCache.clear();
            this.gateChunkCache.clear();
            this.gateChunkSample.clear();
        }
        targets.addAll(wireTargets);
        targets.addAll(gateTargets);
        if (targets.isEmpty()) {
            return;
        }
        for (Location loc : wireTargets) {
            Bukkit.getScheduler().runTaskLater(this.plugin, new restone_drop(loc, false), 2L);
        }
        for (Location loc : gateTargets) {
            Bukkit.getScheduler().runTaskLater(this.plugin, new restone_drop(loc, true), 2L);
        }
        if (NeverLag.RedStoneMessage.booleanValue()) {
            // 同一周期内可能同时命中线缆与逻辑门，公告按优先命中线缆处理
            broadcast(wireTargets.isEmpty() ? gateTargets.get(0) : wireTargets.get(0), wireTargets.isEmpty());
        }
    }

    /**
     * 收集超限的线缆坐标。先按单个坐标判定，再按区块合计判定：大范围线缆网络会把事件
     * 分散到大量坐标上，单坐标计数可能始终低于阀值。
     */
    private void collectTargets(HashMap<Location, Integer> tileMap, HashMap<String, Integer> chunkMap, HashMap<String, Location> sampleMap, int tileLimit, int chunkLimit, List<Location> targets) {
        for (Map.Entry<Location, Integer> entry : tileMap.entrySet()) {
            if (entry.getValue() >= tileLimit && !targets.contains(entry.getKey())) {
                targets.add(entry.getKey());
            }
        }
        if (chunkLimit <= 0) {
            return;
        }
        for (Map.Entry<String, Integer> entry : chunkMap.entrySet()) {
            if (entry.getValue() < chunkLimit) {
                continue;
            }
            Location hottest = this.hottestTile(tileMap, entry.getKey());
            if (hottest == null) {
                hottest = sampleMap.get(entry.getKey());
            }
            if (hottest != null && !targets.contains(hottest)) {
                targets.add(hottest);
            }
        }
    }

    /** 取指定区块内计数最高的部件坐标。 */
    private Location hottestTile(HashMap<Location, Integer> tileMap, String key) {
        Location hottest = null;
        int max = 0;
        for (Map.Entry<Location, Integer> entry : tileMap.entrySet()) {
            Location loc = entry.getKey();
            if (!key.equals(chunkKey(loc))) {
                continue;
            }
            if (entry.getValue() > max) {
                max = entry.getValue();
                hottest = loc;
            }
        }
        return hottest;
    }

    /** 在触发点自身与六个邻面内定位承载指定类型部件的多方块方块。 */
    private Location findPartTile(Block source, List<Class<?>> partClasses) {
        Location found = this.partTileAt(source, partClasses);
        if (found != null) {
            return found;
        }
        World world = source.getWorld();
        if (world == null) {
            return null;
        }
        int x = source.getX();
        int y = source.getY();
        int z = source.getZ();
        Location hit = this.partTileAt(world.getBlockAt(x + 1, y, z), partClasses);
        if (hit == null) hit = this.partTileAt(world.getBlockAt(x - 1, y, z), partClasses);
        if (hit == null) hit = this.partTileAt(world.getBlockAt(x, y + 1, z), partClasses);
        if (hit == null) hit = this.partTileAt(world.getBlockAt(x, y - 1, z), partClasses);
        if (hit == null) hit = this.partTileAt(world.getBlockAt(x, y, z + 1), partClasses);
        if (hit == null) hit = this.partTileAt(world.getBlockAt(x, y, z - 1), partClasses);
        return hit;
    }

    /** 判断该方块是否为承载指定类型部件的多方块方块，是则返回其坐标。 */
    private Location partTileAt(Block block, List<Class<?>> partClasses) {
        if (block == null || block.getTypeId() != this.proxyBlockId) {
            return null;
        }
        Object tile = this.getTileEntity(block);
        if (tile == null || !this.tileClass.isInstance(tile)) {
            return null;
        }
        List<?> parts = this.partList(tile);
        if (parts == null) {
            return null;
        }
        for (Object part : parts) {
            if (this.isPart(part, partClasses)) {
                return block.getLocation();
            }
        }
        return null;
    }

    private Object getTileEntity(Block block) {
        if (this.tileEntityAt == null) {
            return null;
        }
        try {
            return this.tileEntityAt.invoke(block.getWorld(), Integer.valueOf(block.getX()), Integer.valueOf(block.getY()), Integer.valueOf(block.getZ()));
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private List<?> partList(Object tile) {
        try {
            Object parts = tile.getClass().getMethod("jPartList").invoke(tile);
            if (parts instanceof List) {
                return (List<?>)parts;
            }
        }
        catch (Throwable throwable) {
            // 部件列表不可用时按无线缆处理
        }
        return null;
    }

    private boolean isPart(Object part, List<Class<?>> partClasses) {
        if (part == null) {
            return false;
        }
        for (Class<?> partClass1 : partClasses) {
            if (partClass1.isInstance(part)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 移除该坐标上属于指定类型的全部部件。部件被全部移除时多方块方块会自行变为空气。
     * 返回移除的部件数量，0 表示该坐标已不存在目标部件。
     */
    private int removeParts(Location loc, List<Class<?>> partClasses) {
        if (this.remPart == null) {
            return 0;
        }
        Block block = loc.getBlock();
        Object tile = this.getTileEntity(block);
        if (tile == null || !this.tileClass.isInstance(tile)) {
            return 0;
        }
        List<?> parts = this.partList(tile);
        if (parts == null) {
            return 0;
        }
        ArrayList<Object> targets = new ArrayList<Object>();
        for (Object part : parts) {
            if (this.isPart(part, partClasses)) {
                targets.add(part);
            }
        }
        int removed = 0;
        for (Object part : targets) {
            try {
                this.remPart.invoke(tile, part);
                ++removed;
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().warning("[NeverLag] 移除线缆失败 " + formatLocation(loc) + "：" + throwable);
            }
        }
        return removed;
    }

    /** 按配置在该位置生成一次爆炸。生成前后置位标志，避免 AntiExplode 清空方块列表。 */
    private void explode(Location loc) {
        if (!NeverLag.RedStoneExplosion.booleanValue()) {
            return;
        }
        float power = (float)NeverLag.RedStoneExplosionPower;
        World world = loc.getWorld();
        if (world == null || power <= 0.0f) {
            return;
        }
        int max = NeverLag.RedStoneExplosionMaxPerRun;
        if (max > 0 && this.explosionCount >= max) {
            this.plugin.getLogger().info("[NeverLag] 本轮爆炸次数已达上限 " + max + "，跳过 " + formatLocation(loc));
            return;
        }
        ++this.explosionCount;
        pluginExplosion = true;
        try {
            world.createExplosion(loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, power, NeverLag.RedStoneExplosionFire.booleanValue(), NeverLag.RedStoneExplosionBreakBlocks.booleanValue());
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("[NeverLag] 生成爆炸失败 " + formatLocation(loc) + "：" + throwable);
        }
        finally {
            pluginExplosion = false;
        }
        // 只在 FINE 级别留痕：默认日志级别下不输出，需要排查时调高日志等级可见
        this.plugin.getLogger().fine("[NeverLag] 已在 " + formatLocation(loc) + " 生成爆炸，威力 " + power);
    }

    private void broadcast(Location location, boolean gate) {
        String nameList = "";
        int count = 0;
        Entity[] entities = location.getChunk().getEntities();
        for (int i = 0; i < entities.length && count < 3; ++i) {
            Entity entity = entities[i];
            if (!(entity instanceof Player)) {
                continue;
            }
            Player player = (Player)entity;
            if (player.hasMetadata("NPC")) {
                continue;
            }
            nameList = nameList.isEmpty() ? player.getName() : nameList + ", " + player.getName();
            ++count;
        }
        String message = gate ? NeverLag.m_redstone_gate : NeverLag.m_redstone_pr;
        message = message.replace("%Location%", formatLocation(location)).replace("%Player%", nameList);
        if (NeverLag.RedStoneMessage.booleanValue()) {
            Bukkit.getServer().broadcastMessage(message);
        }
        this.plugin.getLogger().info("[NeverLag] 广播：" + message);
    }

    private static String formatLocation(Location loc) {
        World world = loc.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return "[" + worldName + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "]";
    }

    private static String chunkKey(Location loc) {
        World world = loc.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return worldName + ":" + (loc.getBlockX() >> 4) + ":" + (loc.getBlockZ() >> 4);
    }

    private static <K> int bump(HashMap<K, Integer> map, K key) {
        Integer old = map.get(key);
        int now = old == null ? 1 : old.intValue() + 1;
        map.put(key, Integer.valueOf(now));
        return now;
    }

    private static Class<?> resolveClass(String name) {
        try {
            return Class.forName(name);
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static Method resolveMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method method = owner.getMethod(name, params);
            method.setAccessible(true);
            return method;
        }
        catch (Throwable throwable) {
            return null;
        }
    }

    private static Method resolveTileEntityAt() {
        Class<?> craftWorld = resolveClass("org.bukkit.craftbukkit." + CRAFT_PACKAGE + ".CraftWorld");
        if (craftWorld == null) {
            return null;
        }
        return resolveMethod(craftWorld, "getTileEntityAt", int.class, int.class, int.class);
    }

    /** 解析多方块代理方块在服务端的数字 ID，用于与 BlockPhysicsEvent 的变化源比对。 */
    private static int resolveProxyBlockId() {
        try {
            Class<?> proxy = Class.forName("codechicken.multipart.handler.MultipartProxy");
            Object block = proxy.getMethod("block").invoke(null);
            if (block == null) {
                return -1;
            }
            Class<?> magic = resolveClass("org.bukkit.craftbukkit." + CRAFT_PACKAGE + ".util.CraftMagicNumbers");
            if (magic != null) {
                for (Method method : magic.getMethods()) {
                    if (!"getId".equals(method.getName()) || method.getParameterTypes().length != 1 || method.getReturnType() != int.class) {
                        continue;
                    }
                    if (!method.getParameterTypes()[0].isInstance(block)) {
                        continue;
                    }
                    return ((Integer)method.invoke(null, block)).intValue();
                }
            }
            Class<?> blockClass = Class.forName("net.minecraft.block.Block");
            return ((Integer)blockClass.getMethod("getIdFromBlock", blockClass).invoke(null, block)).intValue();
        }
        catch (Throwable throwable) {
            return -1;
        }
    }

    private class restone_drop
    implements Runnable {
        private final Location loc;

        private final boolean gate;

        public restone_drop(Location loc, boolean gate) {
            this.loc = loc;
            this.gate = gate;
        }

        @Override
        public void run() {
            try {
                Block block = this.loc.getBlock();
                int typeId = block.getTypeId();
                if (NeverLag.RedstoneClearType.contains(typeId)) {
                    if (NeverLag.RedStoneDrop.booleanValue()) {
                        block.breakNaturally();
                    } else {
                        block.setType(Material.AIR);
                    }
                    CheckRedstone.this.explode(this.loc);
                    return;
                }
                int removed = CheckRedstone.this.removeParts(this.loc, this.gate ? CheckRedstone.this.gateClasses : CheckRedstone.this.wireClasses);
                if (removed <= 0) {
                    return;
                }
                CheckRedstone.this.plugin.getLogger().info("[NeverLag] 已没收 ProjectRed " + (this.gate ? "逻辑门" : "线缆") + " " + formatLocation(this.loc) + "，数量 " + removed);
                CheckRedstone.this.explode(this.loc);
            }
            catch (Throwable throwable) {
                CheckRedstone.this.plugin.getLogger().warning("[NeverLag] 没收红石失败 " + formatLocation(this.loc) + "：" + throwable);
            }
        }
    }
}
