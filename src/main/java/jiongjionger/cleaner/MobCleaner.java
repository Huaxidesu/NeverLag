package jiongjionger.cleaner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;

/**
 * 异步生物清理。
 *
 * 线程模型：
 *   1. 主线程：取一次世界里活体实体的快照（只复制引用，很快）
 *   2. 异步线程：逐个体判定类别、查中文名，挑出待清理列表
 *   3. 主线程：每 tick 按 BatchSize / TimeBudgetMs 分批 remove()，直到清完再播报
 *
 * 实体增删必须在主线程做，所以真正 remove() 不能异步；把“判定”和“名字解析”放到异步、
 * 把“删除”切成小批并限制每 tick 的时间预算，主线程不会被一次性卡住。
 */
public class MobCleaner {

    public enum Mode {
        /** 定时任务，按配置的类别 */
        SCHEDULED,
        /** 手动 now，按配置的类别 */
        NOW,
        /** 手动 all，三个类别全清 */
        ALL,
        /** 手动指定实体键 */
        KEY
    }

    private final NeverLag plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private int taskId = -1;
    /** 距离下一次定时清理已经过去的秒数，仅供状态查询使用。 */
    private int elapsed;
    /** 本轮已经播报到第几个预警点（warningAt 按下标从大到小走）。 */
    private int warned;
    /** 下一轮定时清理的时间戳（毫秒）。计时以系统时间为准，避免服务器掉 TPS 时周期被拉长。 */
    private long nextRunAt;
    /** 上一次清理的结果摘要，供 /neverlag status 查询。 */
    private String lastSummary = "尚未执行";

    public MobCleaner(NeverLag plugin) {
        this.plugin = plugin;
    }

    /**
     * 重新挂定时器。只用一个每秒执行的任务，预警和清理共用同一个计数器，
     * 这样预热提醒不会和实际清理错拍（两个独立任务在卡服时可能前后错开若干秒）。
     */
    public void reload() {
        cancel();
        if (!MobClearConfig.enabled) {
            plugin.getLogger().info("[NeverLag] 生物清理未开启（mobclear.yml 的 Enable）");
            return;
        }
        elapsed = 0;
        warned = 0;
        nextRunAt = System.currentTimeMillis() + MobClearConfig.delaySeconds * 1000L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                tick();
            }
        }, 20L, 20L);
        StringBuilder sb = new StringBuilder();
        sb.append("[NeverLag] 生物清理已开启，每 ").append(MobClearConfig.delaySeconds).append(" 秒一次");
        if (MobClearConfig.warningAt.isEmpty()) {
            sb.append("，未设置预警");
        } else {
            sb.append("，预警提前 ");
            for (int i = 0; i < MobClearConfig.warningAt.size(); i++) {
                if (i > 0) {
                    sb.append('/');
                }
                sb.append(MobClearConfig.warningAt.get(i));
            }
            sb.append(" 秒");
        }
        plugin.getLogger().info(sb.toString());
    }

    private void tick() {
        if (!plugin.isEnabled()) {
            cancel();
            return;
        }
        // 按系统时间计时，而不是累计任务执行次数：GTNH 服常年跑不满 20 TPS，
        // 用计数会把 1200 秒拖成一两小时，周期越长偏差越明显。
        long now = System.currentTimeMillis();
        int remain = (int) Math.ceil((nextRunAt - now) / 1000.0D);
        if (remain < 0) {
            remain = 0;
        }
        elapsed = Math.max(0, MobClearConfig.delaySeconds - remain);
        // 预警：按从远到近的顺序走，服务器卡顿导致跳秒时也只会补播一条、不会漏光
        while (warned < MobClearConfig.warningAt.size()
                && remain <= MobClearConfig.warningAt.get(warned).intValue()) {
            int seconds = MobClearConfig.warningAt.get(warned).intValue();
            warned++;
            warn(seconds);
        }
        if (remain <= 0) {
            nextRunAt = now + MobClearConfig.delaySeconds * 1000L;
            elapsed = 0;
            warned = 0;
            trigger(Mode.SCHEDULED, null, null);
        }
    }

    /**
     * 到点前的倒计时提醒。和垃圾清理插件走同一条路：{@code broadcastMessage(String)} 纯文本，
     * 一次广播全体，不经过聊天组件，也不经过指令解析，任何服务端都不会丢。
     */
    private void warn(int seconds) {
        String text;
        try {
            text = ChatColor.translateAlternateColorCodes('&', MobClearConfig.warningMessage)
                    .replace("%TIME%", String.valueOf(seconds));
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 预警文案处理失败: " + t);
            return;
        }
        try {
            int got = Bukkit.getServer().broadcastMessage(text);
            if (MobClearConfig.debug) {
                // 注意：broadcastMessage 的返回值是「送达的权限订阅者数」，控制台等非玩家也会计入，
                // 不等于在线玩家数，所以两个值都打出来，避免排查时误判。
                plugin.getLogger().info("[NeverLag] 预警已播报（剩 " + seconds + " 秒，在线玩家 "
                        + Bukkit.getOnlinePlayers().size() + "，广播回执 " + got + "）: " + text);
            }
        } catch (Throwable t) {
            // 服务端关停过程中广播可能抛异常，不影响下一轮清理
            plugin.getLogger().warning("[NeverLag] 预警播报失败: " + t);
        }
    }

    /**
     * 排查用：把世界里现有的生物按类汇总，标出哪些会被当作 Boss 保护。
     *
     * 用法是「先看一眼再清」：模组加了新 Boss、或者怀疑某只怪被判错，跑一次就能对上，
     * 不用等清理完才发现少了东西。★ 表示会被保护，· 表示会被当成普通生物处理。
     */
    public void dumpBosses(CommandSender sender) {
        final Map<String, BossStat> stats = new HashMap<String, BossStat>();
        int total = 0;
        int bossTotal = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                for (LivingEntity entity : world.getLivingEntities()) {
                    try {
                        if (entity instanceof Player || entity.isDead() || !entity.isValid()) {
                            continue;
                        }
                        if (entity instanceof org.bukkit.entity.Hanging) {
                            continue;
                        }
                        total++;
                        Object handle = NmsAccess.handle(entity);
                        String key = NmsAccess.entityKey(entity);
                        if (key == null) {
                            key = entity.getType().getName();
                        }
                        boolean isBoss = MobClearConfig.isConfiguredBoss(key, handle) || MobType.isBoss(handle)
                                || MobType.isThaumcraftGolem(handle);
                        if (isBoss) {
                            bossTotal++;
                        }
                        String name = handle != null ? handle.getClass().getSimpleName() : entity.getType().getName();
                        BossStat stat = stats.get(name);
                        if (stat == null) {
                            stat = new BossStat(name, key, isBoss);
                            stat.maxHealth = entity.getMaxHealth();
                            stat.type = MobType.classify(entity);
                            stats.put(name, stat);
                        }
                        stat.count++;
                        if (entity.getCustomName() != null && entity.getCustomName().trim().length() > 0) {
                            stat.named++;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                sender.sendMessage("§c[NeverLag] 读取世界 " + world.getName() + " 实体失败: " + t);
            }
        }
        List<BossStat> list = new ArrayList<BossStat>(stats.values());
        Collections.sort(list, new Comparator<BossStat>() {
            @Override
            public int compare(BossStat a, BossStat b) {
                if (a.boss != b.boss) {
                    return a.boss ? -1 : 1;
                }
                return b.count - a.count;
            }
        });
        sender.sendMessage("§b[NeverLag] §7世界生物 §f" + total + " §7只，§f" + list.size() + " §7类，"
                + "其中受保护 §f" + bossTotal + " §7只");
        sender.sendMessage("§7★ = 会被保留（Boss / 神秘时代傀儡），· = 按普通生物处理");
        int shown = 0;
        for (BossStat stat : list) {
            if (shown >= 80) {
                sender.sendMessage("§7……还有 " + (list.size() - shown) + " 类未列出");
                break;
            }
            shown++;
            String typeLabel = stat.type == null ? "不参与清理" : stat.type.label();
            sender.sendMessage((stat.boss ? "§6★ " : "§8· ") + "§f" + stat.count + " × " + stat.name
                    + " §7[" + stat.key + "] §7血量 " + trim(stat.maxHealth)
                    + " §7" + typeLabel + (stat.named > 0 ? "，其中命名 " + stat.named : ""));
        }
    }

    private static String trim(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private static final class BossStat {
        final String name;
        final String key;
        final boolean boss;
        int count;
        int named;
        double maxHealth;
        MobType type;

        BossStat(String name, String key, boolean boss) {
            this.name = name;
            this.key = key;
            this.boss = boss;
        }
    }

    public void cancel() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean trigger(final Mode mode, final String filter, final CommandSender sender) {
        if (!running.compareAndSet(false, true)) {
            if (sender != null) {
                sender.sendMessage("§c[NeverLag] 已有清理任务正在执行，请稍后再试");
            }
            return false;
        }
        final long start = System.currentTimeMillis();
        final List<LivingEntity> snapshot = new ArrayList<LivingEntity>();
        for (World world : Bukkit.getWorlds()) {
            try {
                for (LivingEntity entity : world.getLivingEntities()) {
                    snapshot.add(entity);
                    // 在主线程先摸一次 getType()，避免异步首次访问触发内部的类型缓存竞争
                    try {
                        entity.getType();
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[NeverLag] 读取世界 " + world.getName() + " 实体失败: " + t);
            }
        }
        if (sender != null) {
            sender.sendMessage("§a[NeverLag] 正在异步扫描 " + snapshot.size() + " 个生物实体...");
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, new Runnable() {
            @Override
            public void run() {
                ScanResult result;
                try {
                    result = classify(snapshot, mode, filter);
                } catch (Throwable t) {
                    running.set(false);
                    plugin.getLogger().severe("[NeverLag] 清理扫描失败: " + t);
                    t.printStackTrace();
                    return;
                }
                final ScanResult scan = result;
                Bukkit.getScheduler().runTask(plugin, new Runnable() {
                    @Override
                    public void run() {
                        removeBatched(scan, mode, start, sender);
                    }
                });
            }
        });
        return true;
    }

    /**
     * 异步扫描的结果：待清理列表 + 保留统计（命名牌、驯服宠物、Boss）。
     */
    private static final class ScanResult {

        final List<Candidate> candidates;
        final int named;
        final int tamed;
        final int boss;
        /** 三类「没吃的」各自的名字清单，供游戏内分类悬浮展示（名字 → 只数）。 */
        final Map<String, Integer> namedNames;
        final Map<String, Integer> tamedNames;
        final Map<String, Integer> bossNames;

        ScanResult(List<Candidate> candidates, int named, int tamed, int boss,
                Map<String, Integer> namedNames, Map<String, Integer> tamedNames, Map<String, Integer> bossNames) {
            this.candidates = candidates;
            this.named = named;
            this.tamed = tamed;
            this.boss = boss;
            this.namedNames = namedNames;
            this.tamedNames = tamedNames;
            this.bossNames = bossNames;
        }
    }

    private ScanResult classify(List<LivingEntity> snapshot, Mode mode, String filter) {
        List<Candidate> out = new ArrayList<Candidate>(Math.min(snapshot.size(), 4096));
        int named = 0;
        int tamed = 0;
        int boss = 0;
        Map<String, Integer> namedNames = new HashMap<String, Integer>();
        Map<String, Integer> tamedNames = new HashMap<String, Integer>();
        Map<String, Integer> bossNames = new HashMap<String, Integer>();
        for (LivingEntity entity : snapshot) {
            try {
                if (entity == null || entity instanceof Player || entity.isDead() || !entity.isValid()) {
                    continue;
                }
                // 画、展示框、拴绳结这类装饰物不是 LivingEntity，正常进不到这里；
                // 留着这道闸，防止哪天换了取实体的方式把装饰物一起扫进来。
                if (entity instanceof org.bukkit.entity.Hanging) {
                    continue;
                }
                String key = NmsAccess.entityKey(entity);
                if (key == null) {
                    key = entity.getType().getName();
                }
                Object handle = NmsAccess.handle(entity);
                if (MobClearConfig.isConfiguredBoss(key, handle) || MobType.isBoss(handle)) {
                    boss++;
                    count(bossNames, labelOf(key, entity));
                    continue; // Boss 先判，避免带自定义名的 Boss 被算进「命名牌保留」
                }
                if (MobType.isThaumcraftGolem(handle)) {
                    continue; // 神秘时代傀儡与旅行箱是玩家的生产设施，不论是否命名一律保留
                }
                MobType type = resolveType(entity, key);
                String custom = entity.getCustomName();
                // 精英怪的名字是模组自动加的，命中后照常清理，不算「命名牌保留」
                boolean infernal = MobClearConfig.clearInfernal && NmsAccess.isInfernal(handle);
                if (!infernal && custom != null && custom.trim().length() > 0) {
                    // SpecialMobs 这类模组会给怪物自动起名，敌对生物可以按配置忽略名字照清
                    if (!(type == MobType.HOSTILE && MobClearConfig.clearNamedHostile)) {
                        named++;
                        count(namedNames, labelOf(key, entity));
                        continue; // 命名牌命名的一律保留
                    }
                }
                if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
                    tamed++;
                    count(tamedNames, labelOf(key, entity));
                    continue; // 驯服的宠物/马
                }
                if (MobClearConfig.contains(MobClearConfig.exclude, key)) {
                    continue;
                }
                if (mode == Mode.KEY && !matches(entity, key, filter)) {
                    continue;
                }
                if (type == null || !allowed(mode, type)) {
                    continue;
                }
                out.add(new Candidate(entity, type, MobNames.resolve(key, entity.getType().getName())));
            } catch (Throwable ignored) {
                // 单个实体判定失败不影响整轮清理
            }
        }
        return new ScanResult(out, named, tamed, boss, namedNames, tamedNames, bossNames);
    }

    /**
     * 分类悬浮里显示的名字：优先中文名，取不到就用实体类名。
     */
    private static String labelOf(String key, LivingEntity entity) {
        String zh = MobNames.resolve(key, entity.getType().getName());
        return zh == null || zh.trim().length() == 0 ? entity.getType().getName() : zh;
    }

    private MobType resolveType(LivingEntity entity, String key) {
        if (MobClearConfig.contains(MobClearConfig.extraHostile, key)) {
            return MobType.HOSTILE;
        }
        if (MobClearConfig.contains(MobClearConfig.extraFriendly, key)) {
            return MobType.FRIENDLY;
        }
        if (MobClearConfig.contains(MobClearConfig.extraNeutral, key)) {
            return MobType.NEUTRAL;
        }
        return MobType.classify(entity);
    }

    private boolean allowed(Mode mode, MobType type) {
        if (mode == Mode.ALL || mode == Mode.KEY) {
            return true;
        }
        return MobClearConfig.categoryEnabled(type);
    }

    private boolean matches(LivingEntity entity, String key, String filter) {
        if (filter == null || filter.trim().length() == 0) {
            return true;
        }
        String want = normalize(filter);
        if (key != null) {
            if (normalize(key).equals(want)) {
                return true;
            }
            int dot = key.indexOf('.');
            if (dot > 0 && normalize(key.substring(dot + 1)).equals(want)) {
                return true;
            }
        }
        String type = entity.getType().getName();
        if (normalize(type).equals(want)) {
            return true;
        }
        String zh = MobNames.resolve(key, type);
        return zh != null && zh.equals(filter.trim());
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replace('_', '.').replace(" ", "");
    }

    private void removeBatched(ScanResult scan, final Mode mode, final long start, final CommandSender sender) {
        if (scan.candidates.isEmpty()) {
            running.set(false);
            String msg = "§7[NeverLag] 没有符合条件的生物需要清理" + keepSummary(scan);
            if (sender != null) {
                sender.sendMessage(msg);
            }
            plugin.getLogger().info("[NeverLag] 清理结束：没有符合条件的生物" + keepSummary(scan));
            lastSummary = "没有符合条件的生物" + keepSummary(scan);
            return;
        }
        new RemovalTask(scan, mode, start, sender).start();
    }

    /**
     * 列出因为「有自定义名」而被保留的生物，按名字聚合。
     * 用于确认这些名字是玩家起的还是模组自动加的，判断该不该开 ClearNamedHostile。
     */
    public void dumpNamed(CommandSender sender) {
        final Map<String, Integer> counts = new HashMap<String, Integer>();
        final Map<String, String> classes = new HashMap<String, String>();
        final Set<String> infernalNames = new HashSet<String>();
        int total = 0;
        for (World world : Bukkit.getWorlds()) {
            try {
                for (LivingEntity entity : world.getLivingEntities()) {
                    try {
                        if (entity instanceof Player || entity.isDead() || !entity.isValid()) {
                            continue;
                        }
                        String custom = entity.getCustomName();
                        if (custom == null || custom.trim().length() == 0) {
                            continue;
                        }
                        total++;
                        String name = custom.trim();
                        Integer old = counts.get(name);
                        counts.put(name, old == null ? 1 : old + 1);
                        if (!classes.containsKey(name)) {
                            String key = NmsAccess.entityKey(entity);
                            classes.put(name, entity.getClass().getSimpleName() + " [" + (key == null ? "?" : key) + "]");
                        }
                        if (MobType.isInfernal(entity)) {
                            infernalNames.add(name);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable t) {
                sender.sendMessage("§c[NeverLag] 读取世界 " + world.getName() + " 实体失败: " + t);
            }
        }
        sender.sendMessage("§b[NeverLag] §7带自定义名的生物共 §f" + total + " §7只，§f"
                + counts.size() + " §7种名字；§f" + infernalNames.size() + " §7种带精英怪标记");
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                if (!a.getValue().equals(b.getValue())) {
                    return b.getValue().compareTo(a.getValue());
                }
                return a.getKey().compareTo(b.getKey());
            }
        });
        int shown = 0;
        for (Map.Entry<String, Integer> entry : list) {
            if (shown >= 40) {
                sender.sendMessage("§7……还有 " + (list.size() - shown) + " 种名字未列出");
                break;
            }
            String mark = infernalNames.contains(entry.getKey()) ? "§c[精英]" : "§7[普通]";
            sender.sendMessage("§8" + entry.getValue() + " × §f\"" + entry.getKey() + "\" §7"
                    + classes.get(entry.getKey()) + " " + mark);
            shown++;
        }
    }

    /**
     * 输出运行状态：开关、周期、本轮已过时间、下一轮时间与上次清理结果。
     * 管理员用它确认计时器有没有在走，比等一轮播报快得多。
     */
    public void dumpStatus(CommandSender sender) {
        long now = System.currentTimeMillis();
        int remain = (int) Math.max(0, Math.ceil((nextRunAt - now) / 1000.0D));
        StringBuilder head = new StringBuilder();
        head.append("§b[NeverLag] §7生物清理：").append(MobClearConfig.enabled ? "§a开启" : "§c关闭");
        head.append(" §7· 周期 §f").append(MobClearConfig.delaySeconds).append(" §7秒");
        if (!MobClearConfig.warningAt.isEmpty()) {
            head.append(" §7· 预警提前 ");
            for (int i = 0; i < MobClearConfig.warningAt.size(); i++) {
                if (i > 0) {
                    head.append('/');
                }
                head.append(MobClearConfig.warningAt.get(i));
            }
            head.append(" 秒");
        }
        sender.sendMessage(head.toString());
        sender.sendMessage("§7清理类别：敌对 " + onOff(MobClearConfig.cleanHostile)
                + " §7/ 友好 " + onOff(MobClearConfig.cleanFriendly)
                + " §7/ 中立 " + onOff(MobClearConfig.cleanNeutral)
                + " §7· 命名敌对 " + onOff(MobClearConfig.clearNamedHostile)
                + " §7· 排除 " + MobClearConfig.exclude.size()
                + " 项 · Boss " + MobClearConfig.boss.size() + " 项");
        if (MobClearConfig.enabled && taskId != -1) {
            String clock = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(nextRunAt));
            sender.sendMessage("§7本轮已过 §f" + elapsed + " §7秒，下一轮还有 §f" + remain
                    + " §7秒（§f" + clock + "§7）");
        }
        else {
            sender.sendMessage("§c定时任务未运行（taskId=" + taskId + "）");
        }
        sender.sendMessage("§7上次清理：§f" + lastSummary);
        if (running.get()) {
            sender.sendMessage("§e当前有一轮清理正在执行");
        }
    }

    private static String onOff(boolean value) {
        return value ? "§a开" : "§c关";
    }

    private static String keepSummary(ScanResult scan) {
        StringBuilder sb = new StringBuilder();
        if (scan.named > 0) {
            sb.append("，命名牌保留 ").append(scan.named).append(" 只");
        }
        if (scan.tamed > 0) {
            sb.append("，驯服宠物保留 ").append(scan.tamed).append(" 只");
        }
        if (scan.boss > 0) {
            sb.append("，Boss 保留 ").append(scan.boss).append(" 只");
        }
        return sb.toString();
    }

    private void finish(Map<MobType, Map<String, Integer>> counts, int total, Mode mode, long start,
            CommandSender sender, ScanResult scan) {
        running.set(false);
        long elapsed = System.currentTimeMillis() - start;
        lastSummary = total + " 只，耗时 " + elapsed + " 毫秒" + keepSummary(scan);
        plugin.getLogger().info("[NeverLag] 清理完成：" + total + " 只，耗时 " + elapsed + " 毫秒"
                + keepSummary(scan));
        if (total > 0) {
            try {
                ChatReport.broadcast(plugin, counts, total, elapsed, scan.named, scan.tamed, scan.boss,
                        scan.namedNames, scan.tamedNames, scan.bossNames);
            }
            catch (Throwable t) {
                // 播报出问题（例如换服后聊天组件类缺失）也不能挡住执行者这条回执
                plugin.getLogger().warning("[NeverLag] 播报失败: " + t);
            }
        }
        if (sender != null) {
            sender.sendMessage("§a[NeverLag] 清理完成，共 " + total + " 只，耗时 " + elapsed + " 毫秒");
        }
    }

    private static void count(Map<MobType, Map<String, Integer>> counts, MobType type, String name) {
        Map<String, Integer> map = counts.get(type);
        if (map == null) {
            map = new HashMap<String, Integer>();
            counts.put(type, map);
        }
        Integer old = map.get(name);
        map.put(name, old == null ? 1 : old + 1);
    }

    /**
     * 名字清单自增，用于三类「没吃的」的分类悬浮。
     */
    private static void count(Map<String, Integer> map, String name) {
        Integer old = map.get(name);
        map.put(name, old == null ? 1 : old + 1);
    }

    private static final class Candidate {
        final LivingEntity entity;
        final MobType type;
        final String name;

        Candidate(LivingEntity entity, MobType type, String name) {
            this.entity = entity;
            this.type = type;
            this.name = name;
        }
    }

    /**
     * 分批删除：每 tick 最多 BatchSize 只，且最多占用 TimeBudgetMs 毫秒。
     */
    private final class RemovalTask implements Runnable {

        private final ScanResult scan;
        private final Mode mode;
        private final long start;
        private final CommandSender sender;
        private final Map<MobType, Map<String, Integer>> counts = new EnumMap<MobType, Map<String, Integer>>(MobType.class);
        private int index;
        private int removed;
        private int taskId = -1;

        RemovalTask(ScanResult scan, Mode mode, long start, CommandSender sender) {
            this.scan = scan;
            this.mode = mode;
            this.start = start;
            this.sender = sender;
        }

        void start() {
            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this, 0L, 1L);
        }

        @Override
        public void run() {
            if (!plugin.isEnabled()) {
                Bukkit.getScheduler().cancelTask(taskId);
                running.set(false);
                return;
            }
            long deadline = System.nanoTime() + MobClearConfig.timeBudgetMs * 1000000L;
            int doneThisTick = 0;
            List<Candidate> list = scan.candidates;
            while (index < list.size() && doneThisTick < MobClearConfig.batchSize && System.nanoTime() < deadline) {
                Candidate candidate = list.get(index++);
                doneThisTick++;
                LivingEntity entity = candidate.entity;
                try {
                    if (entity.isDead() || !entity.isValid() || entity instanceof Player) {
                        continue;
                    }
                    if (entity.hasMetadata("NPC") || entity.hasMetadata("MyPet")) {
                        continue;
                    }
                    entity.remove();
                    removed++;
                    count(counts, candidate.type, candidate.name);
                } catch (Throwable ignored) {
                }
            }
            if (index < list.size()) {
                return;
            }
            Bukkit.getScheduler().cancelTask(taskId);
            finish(counts, removed, mode, start, sender, scan);
        }
    }
}
