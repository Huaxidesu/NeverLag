package jiongjionger.cleaner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * 生物清理的独立配置（plugins/NeverLag/mobclear.yml）。
 *
 * 单独放一个文件，是为了不动服务器上已经调好的旧 config.yml——
 * Bukkit 1.7.10 的 YamlConfiguration 保存时会丢掉注释，覆盖旧文件代价太大。
 */
public final class MobClearConfig {

    public static boolean enabled = true;
    public static int delaySeconds = 1800;
    /** 清理前的预警秒数，已去重并按从大到小排好；空表示不预警。 */
    public static final List<Integer> warningAt = new ArrayList<Integer>();
    /** 预警文案，%TIME% 替换成剩余秒数。 */
    public static String warningMessage = "&b[NeverLag] &a生物清理将在 &e%TIME% &a秒后开始，请及时离开刷怪场";
    public static boolean cleanHostile = true;
    public static boolean cleanFriendly = true;
    public static boolean cleanNeutral = true;
    public static int batchSize = 200;
    public static int timeBudgetMs = 4;
    public static boolean broadcast = true;
    public static boolean debug = false;
    /** 敌对生物即使带自定义名也清掉（默认关：任何命名的生物都保留）。 */
    public static boolean clearNamedHostile = false;
    /** 精英怪（InfernalMobs）按持久 NBT 识别，命中后无视自定义名照常清理。 */
    public static boolean clearInfernal = true;
    /** 类名里带 Boss 的实体一律当 Boss 保护（防没实现血条接口的模组 Boss 被误清）。 */
    public static boolean protectBossNamed = true;
    public static String message = "&b[NeverLag]&a本次清理了 &c%COUNT% &a只生物";
    /** 命名牌生物的保留说明，%NAMED% 为数量；置空则不播报。 */
    public static String namedMessage = "&7（命名的 &f%NAMED% &7只太硬，卡牙，这次没吃）";
    /** 驯服宠物的保留说明，%TAMED% 为数量。 */
    public static String tamedMessage = "&7（&f%TAMED% &7只驯服宠物，下不去口）";
    /** Boss 的保留说明，%BOSS% 为数量。 */
    public static String bossMessage = "&7（&f%BOSS% &7只 Boss，打不过）";

    public static final Set<String> extraHostile = new HashSet<String>();
    public static final Set<String> extraFriendly = new HashSet<String>();
    public static final Set<String> extraNeutral = new HashSet<String>();
    public static final Set<String> exclude = new HashSet<String>();
    public static final Set<String> boss = new HashSet<String>();

    /**
     * 整合包（GTNH 2.9.x，229 个模组）里全部 Boss 的类名，扫描 jar 整理而成。
     * 有血条的 Boss 靠 IBossDisplayData 就能自动认出来，这份名单主要兜住两类：
     * 没实现血条接口的（暮色小 Boss、混沌守卫以外的龙）和以后换版本判不出来的。
     */
    private static final List<String> DEFAULT_BOSS = Arrays.asList(
            // 暮色森林
            "EntityTFHydra", "EntityTFUrGhast", "EntityTFNaga", "EntityTFLich", "EntityTFSnowQueen",
            "EntityTFMinoshroom", "EntityTFKnightPhantom", "EntityTFAlphaYeti",
            // 神秘时代
            "EntityEldritchWarden", "EntityEldritchGolem", "EntityCultistLeader",
            "EntityCultistPortal", "EntityTaintacleGiant", "EntityThaumcraftBoss",
            // 植物魔法
            "EntityDoppleganger",
            // 龙之研究
            "EntityChaosGuardian", "EntityCustomDragon",
            // 星系
            "EntitySkeletonBoss", "EntityCreeperBoss",
            // 星空
            "EntityBlazeBoss", "EntityCrystalBoss", "EntityGhastBoss", "EntitySlimeBoss", "EntityWolfBoss",
            // 末地扩展
            "EntityBossDragon", "EntityBossEnderDemon", "EntityMiniBossEnderEye", "EntityMiniBossFireFiend",
            // 匠魂
            "KingBlueSlime",
            // 巫术
            "EntityBabaYaga", "EntityDeath", "EntityGoblinGulg", "EntityGoblinMog", "EntityHornedHuntsman",
            "EntityLeonard", "EntityLilith", "EntityLordOfTorment", "EntityReflection",
            // 阿蒙拉
            "EntityMummyBoss",
            // 未来科技
            "EntityRespawnedDragon",
            // 原版
            "EntityWither", "EntityDragon");

    private MobClearConfig() {
    }

    public static File file(Plugin plugin) {
        return new File(plugin.getDataFolder(), "mobclear.yml");
    }

    public static void load(Plugin plugin) {
        File file = file(plugin);
        if (!file.isFile()) {
            saveDefault(plugin, file);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("Enable", true);
        delaySeconds = Math.max(5, cfg.getInt("Delay", 1800));
        warningMessage = cfg.getString("WarningMessage", warningMessage);
        loadWarnings(cfg.getIntegerList("WarningAt"));
        cleanHostile = cfg.getBoolean("Clean.Hostile", true);
        cleanFriendly = cfg.getBoolean("Clean.Friendly", true);
        cleanNeutral = cfg.getBoolean("Clean.Neutral", true);
        batchSize = Math.max(10, cfg.getInt("BatchSize", 200));
        timeBudgetMs = Math.max(1, cfg.getInt("TimeBudgetMs", 4));
        broadcast = cfg.getBoolean("Broadcast", true);
        debug = cfg.getBoolean("Debug", false);
        clearNamedHostile = cfg.getBoolean("ClearNamedHostile", false);
        clearInfernal = cfg.getBoolean("ClearInfernal", true);
        protectBossNamed = cfg.getBoolean("ProtectBossNamed", true);
        message = cfg.getString("Message", message);
        namedMessage = cfg.getString("NamedMessage", namedMessage);
        tamedMessage = cfg.getString("TamedMessage", tamedMessage);
        bossMessage = cfg.getString("BossMessage", bossMessage);

        fill(extraHostile, cfg.getStringList("ExtraHostile"));
        fill(extraFriendly, cfg.getStringList("ExtraFriendly"));
        fill(extraNeutral, cfg.getStringList("ExtraNeutral"));
        fill(exclude, cfg.getStringList("Exclude"));
        fill(boss, cfg.getStringList("Boss"));
        if (boss.isEmpty()) {
            fill(boss, DEFAULT_BOSS);
        }
    }

    /**
     * 读取并规整预警时间点：丢掉非正数与不小于 Delay 的值，去重后从大到小排序。
     * 把 Delay 调小时，配置里过大的预警点自动失效，不必回头改列表。
     */
    private static void loadWarnings(List<Integer> values) {
        warningAt.clear();
        if (values == null || values.isEmpty()) {
            return;
        }
        Set<Integer> unique = new HashSet<Integer>();
        for (Integer value : values) {
            if (value == null || value.intValue() <= 0 || value.intValue() >= delaySeconds) {
                continue;
            }
            unique.add(value);
        }
        warningAt.addAll(unique);
        Collections.sort(warningAt, Collections.reverseOrder());
    }

    private static void fill(Set<String> target, List<String> values) {
        target.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                target.add(value.trim());
            }
        }
    }

    public static boolean categoryEnabled(MobType type) {
        if (type == null) {
            return false;
        }
        switch (type) {
            case HOSTILE:
                return cleanHostile;
            case FRIENDLY:
                return cleanFriendly;
            case NEUTRAL:
                return cleanNeutral;
            default:
                return false;
        }
    }

    /**
     * 「Boss」配置项是否命中这个实体。除了实体键（TwilightForest.Hydra），
     * 也认完整类名和简单类名（EntityTFHydra），这样不依赖模组怎么注册实体也能写死保护。
     */
    public static boolean isConfiguredBoss(String entityKey, Object nmsHandle) {
        if (contains(boss, entityKey)) {
            return true;
        }
        if (nmsHandle == null) {
            return false;
        }
        Class<?> clazz = nmsHandle.getClass();
        return contains(boss, clazz.getName()) || contains(boss, clazz.getSimpleName());
    }

    public static boolean contains(Set<String> keys, String entityKey) {
        if (entityKey == null || keys.isEmpty()) {
            return false;
        }
        String lower = entityKey.toLowerCase(Locale.ROOT);
        if (keys.contains(entityKey)) {
            return true;
        }
        for (String key : keys) {
            String k = key.toLowerCase(Locale.ROOT);
            if (k.equals(lower) || lower.endsWith("." + k)) {
                return true;
            }
        }
        return false;
    }

    private static void saveDefault(Plugin plugin, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            InputStream in = plugin.getResource("mobclear.yml");
            if (in == null) {
                return;
            }
            OutputStream out = new FileOutputStream(file);
            try {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            } finally {
                out.close();
                in.close();
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 生成 mobclear.yml 失败: " + t);
        }
    }
}
