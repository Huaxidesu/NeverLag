package jiongjionger.cleaner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.plugin.Plugin;

/**
 * 模组自动拼名用到的词表。
 *
 * 数据由 tools/gen_autonames.py 从 GTNH 汉化包里 InfernalMobs 的语言文件提取：
 * 精英怪的名字是「前缀 + 生物名」或「生物名 + 后缀」，例如「剧毒的僵尸」「僵尸·不死者」。
 * 这些前缀后缀一共 142 条，全部收录在 autoname_zh_cn.txt，运行时只做包含匹配。
 *
 * 只用来给 {@link MobCleaner#isAutoNamed} 提供一条判据：名字里出现这些词，
 * 说明不是玩家起的，可以照常清理。玩家自取的名字不会命中。
 */
public final class AutoNames {

    private static final List<String> WORDS = new ArrayList<String>();
    private static boolean loaded;

    private AutoNames() {
    }

    public static synchronized void load(Plugin plugin) {
        if (loaded) {
            return;
        }
        loaded = true;
        InputStream in = plugin.getResource("autoname_zh_cn.txt");
        if (in == null) {
            plugin.getLogger().warning("[NeverLag] 未找到 autoname_zh_cn.txt，自动拼名判定将只靠种类词");
            return;
        }
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() < 2 || line.startsWith("#")) {
                    continue;
                }
                if (!WORDS.contains(line)) {
                    WORDS.add(line);
                }
            }
            plugin.getLogger().info("[NeverLag] 已加载 " + WORDS.size() + " 条自动拼名词");
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 读取自动拼名词表失败: " + t);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 名字里含任意一个已知词即返回 true。
     * 词表都是两个汉字以上（最短的是「拜拜」「巫师」），不会因为单字造成大面积误判。
     */
    public static boolean matches(String name) {
        if (name == null || name.length() < 2) {
            return false;
        }
        for (int i = 0; i < WORDS.size(); i++) {
            if (name.contains(WORDS.get(i))) {
                return true;
            }
        }
        return false;
    }

    public static int size() {
        return WORDS.size();
    }
}
