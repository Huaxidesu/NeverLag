package jiongjionger.listener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.Plugin;

/**
 * 生物自然生成拦截与数量限制配置（plugins/NeverLag/spawnblock.yml）。
 *
 * 独立存放配置，避免修改已有的 config.yml 导致注释丢失。
 */
public final class SpawnBlockConfig {

    public enum LimitScope {
        WORLD,
        SERVER,
        CHUNK
    }

    public static boolean enabled = true;
    public static boolean debug = false;
    public static boolean blockWaterMob = true;
    public static boolean blockAmbient = true;
    public static LimitScope limitScope = LimitScope.WORLD;
    public static long limitCacheMs = 2000L;

    private static final EnumSet<CreatureSpawnEvent.SpawnReason> blockedReasons =
            EnumSet.of(CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN);
    private static final Set<String> blockedMobs = new HashSet<String>();
    private static final Map<String, Integer> limitRules = new HashMap<String, Integer>();

    private SpawnBlockConfig() {
    }

    public static File file(Plugin plugin) {
        return new File(plugin.getDataFolder(), "spawnblock.yml");
    }

    public static void load(Plugin plugin) {
        File file = file(plugin);
        if (!file.isFile()) {
            saveDefault(plugin, file);
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        enabled = cfg.getBoolean("Enable", true);
        debug = cfg.getBoolean("Debug", false);
        limitCacheMs = Math.max(0L, cfg.getLong("LimitCacheMs", 2000L));
        limitScope = parseScope(cfg.getString("LimitScope", "WORLD"));

        loadReasons(cfg.getStringList("Reasons"));
        loadMobs(cfg.getStringList("Mobs"));
        loadLimits(cfg.getConfigurationSection("Limits"));
    }

    private static LimitScope parseScope(String raw) {
        if (raw == null) {
            return LimitScope.WORLD;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return LimitScope.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return LimitScope.WORLD;
        }
    }

    private static void loadReasons(List<String> values) {
        blockedReasons.clear();
        if (values == null || values.isEmpty()) {
            blockedReasons.add(CreatureSpawnEvent.SpawnReason.NATURAL);
            blockedReasons.add(CreatureSpawnEvent.SpawnReason.CHUNK_GEN);
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            try {
                blockedReasons.add(CreatureSpawnEvent.SpawnReason.valueOf(normalized));
            } catch (IllegalArgumentException ignored) {
                // 忽略拼写错误或当前服务端版本不存在的 SpawnReason
            }
        }
    }

    private static void loadMobs(List<String> values) {
        blockedMobs.clear();
        blockWaterMob = false;
        blockAmbient = false;
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            if (isWaterMobKeyword(normalized)) {
                blockWaterMob = true;
            } else if (isAmbientKeyword(normalized)) {
                blockAmbient = true;
            } else {
                blockedMobs.add(normalized);
            }
        }
    }

    private static void loadLimits(ConfigurationSection section) {
        limitRules.clear();
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (key == null) {
                continue;
            }
            String normalized = key.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                continue;
            }
            int limit = section.getInt(key, -1);
            if (limit >= 0) {
                limitRules.put(normalized, Integer.valueOf(limit));
            }
        }
    }

    public static boolean isWaterMobKeyword(String normalized) {
        return "watermob".equals(normalized) || "water_mob".equals(normalized) || "water".equals(normalized);
    }

    public static boolean isAmbientKeyword(String normalized) {
        return "ambient".equals(normalized);
    }

    public static boolean isBlockedReason(CreatureSpawnEvent.SpawnReason reason) {
        return reason != null && blockedReasons.contains(reason);
    }

    public static boolean hasBlockRules() {
        return blockWaterMob || blockAmbient || !blockedMobs.isEmpty();
    }

    public static boolean hasLimitRules() {
        return !limitRules.isEmpty();
    }

    public static boolean containsMob(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return blockedMobs.contains(identifier.toLowerCase(Locale.ROOT));
    }

    public static Set<String> limitRuleKeys() {
        return Collections.unmodifiableSet(limitRules.keySet());
    }

    public static int getLimit(String ruleKey) {
        Integer value = limitRules.get(ruleKey);
        return value == null ? -1 : value.intValue();
    }

    private static void saveDefault(Plugin plugin, File target) {
        InputStream in = plugin.getResource("spawnblock.yml");
        if (in == null) {
            return;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        OutputStream out = null;
        try {
            out = new FileOutputStream(target);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 写入默认 spawnblock.yml 失败: " + t);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            try {
                in.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
