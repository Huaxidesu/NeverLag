package jiongjionger.cleaner;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import jiongjionger.NeverLag;
import org.bukkit.Bukkit;
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

    public MobCleaner(NeverLag plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cancel();
        if (!MobClearConfig.enabled) {
            plugin.getLogger().info("[NeverLag] 生物清理未开启（mobclear.yml 的 Enable）");
            return;
        }
        long ticks = Math.max(5, MobClearConfig.delaySeconds) * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            @Override
            public void run() {
                trigger(Mode.SCHEDULED, null, null);
            }
        }, ticks, ticks);
        plugin.getLogger().info("[NeverLag] 生物清理已开启，每 " + MobClearConfig.delaySeconds + " 秒一次");
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

        ScanResult(List<Candidate> candidates, int named, int tamed, int boss) {
            this.candidates = candidates;
            this.named = named;
            this.tamed = tamed;
            this.boss = boss;
        }
    }

    private ScanResult classify(List<LivingEntity> snapshot, Mode mode, String filter) {
        List<Candidate> out = new ArrayList<Candidate>(Math.min(snapshot.size(), 4096));
        int named = 0;
        int tamed = 0;
        int boss = 0;
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
                if (MobClearConfig.contains(MobClearConfig.boss, key) || MobType.isBoss(entity)) {
                    boss++;
                    continue; // Boss 先判，避免带自定义名的 Boss 被算进「命名牌保留」
                }
                MobType type = resolveType(entity, key);
                String custom = entity.getCustomName();
                if (custom != null && custom.trim().length() > 0) {
                    // SpecialMobs 这类模组会给怪物自动起名，敌对生物可以按配置忽略名字照清
                    if (!(type == MobType.HOSTILE && MobClearConfig.clearNamedHostile)) {
                        named++;
                        continue; // 命名牌命名的一律保留
                    }
                }
                if (entity instanceof Tameable && ((Tameable) entity).isTamed()) {
                    tamed++;
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
        return new ScanResult(out, named, tamed, boss);
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
            return;
        }
        new RemovalTask(scan, mode, start, sender).start();
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
        plugin.getLogger().info("[NeverLag] 清理完成：" + total + " 只，耗时 " + elapsed + " 毫秒"
                + keepSummary(scan));
        if (total > 0) {
            try {
                ChatReport.broadcast(plugin, counts, total, elapsed, scan.named, scan.tamed, scan.boss);
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
