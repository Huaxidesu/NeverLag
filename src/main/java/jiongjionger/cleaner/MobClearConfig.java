package jiongjionger.cleaner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
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
    public static boolean cleanHostile = true;
    public static boolean cleanFriendly = true;
    public static boolean cleanNeutral = true;
    public static int batchSize = 200;
    public static int timeBudgetMs = 4;
    public static boolean broadcast = true;
    public static boolean debug = false;
    /** 敌对生物即使带自定义名也清掉（SpecialMobs 等模组会给怪物自动起名）。 */
    public static boolean clearNamedHostile = true;
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

    private static final List<String> DEFAULT_BOSS = Arrays.asList(
            "TwilightForest.Hydra",
            "TwilightForest.UrGhast",
            "TwilightForest.Naga",
            "TwilightForest.Lich",
            "TwilightForest.SnowQueen",
            "TwilightForest.Minoshroom",
            "TwilightForest.KnightPhantom",
            "DraconicEvolution.ChaosGuardian",
            "DraconicEvolution.EnderDragon");

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
        cleanHostile = cfg.getBoolean("Clean.Hostile", true);
        cleanFriendly = cfg.getBoolean("Clean.Friendly", true);
        cleanNeutral = cfg.getBoolean("Clean.Neutral", true);
        batchSize = Math.max(10, cfg.getInt("BatchSize", 200));
        timeBudgetMs = Math.max(1, cfg.getInt("TimeBudgetMs", 4));
        broadcast = cfg.getBoolean("Broadcast", true);
        debug = cfg.getBoolean("Debug", false);
        clearNamedHostile = cfg.getBoolean("ClearNamedHostile", true);
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
