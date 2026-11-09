package jiongjionger.cleaner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.plugin.Plugin;

/**
 * 生物中文名表。
 *
 * 数据由 tools/gen_mobnames.py 从 GTNH 整合包自带的汉化里提取
 * （原版 assets + mods 内 lang + config/txloader 的 GTNH 中文包），
 * 以 mobnames_zh_cn.txt 打进 jar，运行时只做查表。
 */
public final class MobNames {

    private static final Map<String, String> NAMES = new HashMap<String, String>();
    private static boolean loaded;

    private MobNames() {
    }

    public static synchronized void load(Plugin plugin) {
        if (loaded) {
            return;
        }
        loaded = true;
        InputStream in = plugin.getResource("mobnames_zh_cn.txt");
        if (in == null) {
            plugin.getLogger().warning("[NeverLag] 未找到 mobnames_zh_cn.txt，生物名将回退为英文键名");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#")) {
                    continue;
                }
                int idx = line.indexOf('=');
                if (idx <= 0 || idx == line.length() - 1) {
                    continue;
                }
                NAMES.put(line.substring(0, idx).trim().toLowerCase(Locale.ROOT), line.substring(idx + 1).trim());
            }
            plugin.getLogger().info("[NeverLag] 已加载 " + NAMES.size() + " 条生物中文名");
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 读取生物中文名失败: " + t);
        } finally {
            close(reader);
        }
    }

    private static void close(BufferedReader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 依次尝试 EntityList 键、去掉 modid 前缀的键、Bukkit 实体类型名。
     */
    public static String resolve(String entityKey, String bukkitTypeName) {
        if (entityKey != null) {
            String hit = get(entityKey);
            if (hit != null) {
                return hit;
            }
            int dot = entityKey.indexOf('.');
            if (dot > 0 && dot < entityKey.length() - 1) {
                hit = get(entityKey.substring(dot + 1));
                if (hit != null) {
                    return hit;
                }
            }
        }
        if (bukkitTypeName != null) {
            String hit = get(bukkitTypeName);
            if (hit != null) {
                return hit;
            }
            int underscore = bukkitTypeName.lastIndexOf('_');
            if (underscore > 0 && underscore < bukkitTypeName.length() - 1) {
                hit = get(bukkitTypeName.substring(underscore + 1));
                if (hit != null) {
                    return hit;
                }
            }
        }
        return entityKey == null ? (bukkitTypeName == null ? "未知生物" : bukkitTypeName) : entityKey;
    }

    public static String get(String key) {
        if (key == null) {
            return null;
        }
        return NAMES.get(key.toLowerCase(Locale.ROOT));
    }

    public static int size() {
        return NAMES.size();
    }

    public static java.util.Set<String> keys() {
        return NAMES.keySet();
    }
}
