package jiongjionger.listener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jiongjionger.NeverLag;
import jiongjionger.cleaner.MobNames;
import jiongjionger.cleaner.NmsAccess;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

/**
 * 拦截指定生物的自然生成并按规则限制自然生成数量，放行召唤法术、刷怪蛋、刷怪笼等途径。
 *
 * Crucible 服务端下 bukkit.yml 的 spawn-limits 设为 0 仍可能因区块生成或 Forge 刷怪路径
 * 产生水生生物与蝙蝠，因此在 CreatureSpawnEvent 中按 SpawnReason 精确拦截。
 */
public class NaturalSpawnBlocker implements Listener {

    private static final String NMS_WATER_MOB = "net.minecraft.entity.passive.EntityWaterMob";
    private static final String NMS_AMBIENT = "net.minecraft.entity.passive.EntityAmbientCreature";

    private static final class CountEntry {
        int count;
        final long expireAtMs;

        CountEntry(int count, long expireAtMs) {
            this.count = count;
            this.expireAtMs = expireAtMs;
        }
    }

    private final NeverLag plugin;
    /** 按 NMS 实体类缓存规则匹配结果，避免每次刷怪重复做反射与查表；reload 时清空。 */
    private final Map<Class<?>, Boolean> blockCache = new ConcurrentHashMap<Class<?>, Boolean>();
    private final Map<Class<?>, List<String>> limitRuleCache = new ConcurrentHashMap<Class<?>, List<String>>();
    private final Map<String, CountEntry> countCache = new ConcurrentHashMap<String, CountEntry>();

    public NaturalSpawnBlocker(NeverLag plg) {
        this.plugin = plg;
        plg.getServer().getPluginManager().registerEvents(this, plg);
    }

    public void clearCache() {
        this.blockCache.clear();
        this.limitRuleCache.clear();
        this.countCache.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!SpawnBlockConfig.enabled) {
            return;
        }
        if (!SpawnBlockConfig.hasBlockRules() && !SpawnBlockConfig.hasLimitRules()) {
            return;
        }
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (!SpawnBlockConfig.isBlockedReason(reason)) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        if (SpawnBlockConfig.hasBlockRules() && shouldBlock(entity)) {
            event.setCancelled(true);
            logIntercept("已拦截自然生成", entity, reason, null);
            return;
        }
        if (SpawnBlockConfig.hasLimitRules()) {
            checkAndUpdateLimits(event, entity, reason);
        }
    }

    private void checkAndUpdateLimits(CreatureSpawnEvent event, LivingEntity entity,
            CreatureSpawnEvent.SpawnReason reason) {
        List<String> matchedRules = matchingLimitRules(entity);
        if (matchedRules.isEmpty()) {
            return;
        }
        Location loc = event.getLocation();
        SpawnBlockConfig.LimitScope scope = SpawnBlockConfig.limitScope;
        List<CountEntry> entries = new ArrayList<CountEntry>(matchedRules.size());

        for (String ruleKey : matchedRules) {
            int limit = SpawnBlockConfig.getLimit(ruleKey);
            if (limit <= 0) {
                event.setCancelled(true);
                logIntercept("已按数量上限(0)拦截自然生成", entity, reason, ruleKey);
                return;
            }
            if (scope == SpawnBlockConfig.LimitScope.CHUNK) {
                int current = countInChunk(loc.getChunk(), ruleKey);
                if (current >= limit) {
                    event.setCancelled(true);
                    logIntercept("已达区块上限(" + current + "/" + limit + ")拦截自然生成", entity, reason, ruleKey);
                    return;
                }
            } else {
                CountEntry entry = getOrRefreshCount(ruleKey, loc.getWorld(), scope);
                if (entry.count >= limit) {
                    event.setCancelled(true);
                    logIntercept("已达数量上限(" + entry.count + "/" + limit + ")拦截自然生成", entity, reason, ruleKey);
                    return;
                }
                entries.add(entry);
            }
        }
        // 本次放行后同步递增缓存计数，避免同批刷怪超出上限
        for (CountEntry entry : entries) {
            entry.count++;
        }
    }

    private CountEntry getOrRefreshCount(String ruleKey, World world, SpawnBlockConfig.LimitScope scope) {
        String scopeKey = (scope == SpawnBlockConfig.LimitScope.WORLD && world != null)
                ? world.getName() + "|" + ruleKey
                : "*|" + ruleKey;
        long now = System.currentTimeMillis();
        CountEntry cached = this.countCache.get(scopeKey);
        if (cached != null && now < cached.expireAtMs) {
            return cached;
        }
        int count = (scope == SpawnBlockConfig.LimitScope.WORLD && world != null)
                ? countInWorld(world, ruleKey)
                : countInServer(ruleKey);
        CountEntry fresh = new CountEntry(count, now + SpawnBlockConfig.limitCacheMs);
        this.countCache.put(scopeKey, fresh);
        return fresh;
    }

    private int countInServer(String ruleKey) {
        int total = 0;
        for (World w : Bukkit.getWorlds()) {
            total += countInWorld(w, ruleKey);
        }
        return total;
    }

    private int countInWorld(World world, String ruleKey) {
        int total = 0;
        for (LivingEntity living : world.getLivingEntities()) {
            if (!living.isDead() && matchingLimitRules(living).contains(ruleKey)) {
                total++;
            }
        }
        return total;
    }

    private int countInChunk(Chunk chunk, String ruleKey) {
        if (chunk == null) {
            return 0;
        }
        int total = 0;
        for (Entity e : chunk.getEntities()) {
            if (e instanceof LivingEntity && !e.isDead() && matchingLimitRules((LivingEntity) e).contains(ruleKey)) {
                total++;
            }
        }
        return total;
    }

    private boolean shouldBlock(LivingEntity entity) {
        Object handle = NmsAccess.handle(entity);
        if (handle != null) {
            Class<?> nmsClass = handle.getClass();
            Boolean cached = this.blockCache.get(nmsClass);
            if (cached != null) {
                return cached.booleanValue();
            }
            boolean blocked = evaluateBlock(entity, nmsClass);
            this.blockCache.put(nmsClass, Boolean.valueOf(blocked));
            return blocked;
        }
        return evaluateBlock(entity, null);
    }

    private boolean evaluateBlock(LivingEntity entity, Class<?> nmsClass) {
        if (SpawnBlockConfig.blockWaterMob && isWaterMob(entity, nmsClass)) {
            return true;
        }
        if (SpawnBlockConfig.blockAmbient && isAmbient(entity, nmsClass)) {
            return true;
        }
        String typeName = entity.getType() != null ? entity.getType().name() : null;
        if (SpawnBlockConfig.containsMob(typeName)) {
            return true;
        }
        String entityKey = NmsAccess.entityKey(entity);
        if (SpawnBlockConfig.containsMob(entityKey)) {
            return true;
        }
        String zhName = MobNames.resolve(entityKey, typeName);
        if (SpawnBlockConfig.containsMob(zhName)) {
            return true;
        }
        if (nmsClass != null) {
            if (SpawnBlockConfig.containsMob(nmsClass.getSimpleName())
                    || SpawnBlockConfig.containsMob(nmsClass.getName())) {
                return true;
            }
        }
        return false;
    }

    private List<String> matchingLimitRules(LivingEntity entity) {
        Object handle = NmsAccess.handle(entity);
        if (handle != null) {
            Class<?> nmsClass = handle.getClass();
            List<String> cached = this.limitRuleCache.get(nmsClass);
            if (cached != null) {
                return cached;
            }
            List<String> matched = evaluateLimitRules(entity, nmsClass);
            this.limitRuleCache.put(nmsClass, matched);
            return matched;
        }
        return evaluateLimitRules(entity, null);
    }

    private List<String> evaluateLimitRules(LivingEntity entity, Class<?> nmsClass) {
        String typeName = entity.getType() != null ? entity.getType().name() : null;
        String entityKey = NmsAccess.entityKey(entity);
        String zhName = MobNames.resolve(entityKey, typeName);
        boolean waterMob = isWaterMob(entity, nmsClass);
        boolean ambient = isAmbient(entity, nmsClass);

        String typeLower = lowerOrNull(typeName);
        String keyLower = lowerOrNull(entityKey);
        String zhLower = lowerOrNull(zhName);
        String simpleLower = nmsClass != null ? lowerOrNull(nmsClass.getSimpleName()) : null;
        String fullLower = nmsClass != null ? lowerOrNull(nmsClass.getName()) : null;

        List<String> matched = null;
        for (String ruleKey : SpawnBlockConfig.limitRuleKeys()) {
            boolean hit = (waterMob && SpawnBlockConfig.isWaterMobKeyword(ruleKey))
                    || (ambient && SpawnBlockConfig.isAmbientKeyword(ruleKey))
                    || ruleKey.equals(typeLower)
                    || ruleKey.equals(keyLower)
                    || ruleKey.equals(zhLower)
                    || ruleKey.equals(simpleLower)
                    || ruleKey.equals(fullLower);
            if (hit) {
                if (matched == null) {
                    matched = new ArrayList<String>(2);
                }
                matched.add(ruleKey);
            }
        }
        return matched == null ? Collections.<String>emptyList() : Collections.unmodifiableList(matched);
    }

    private static boolean isWaterMob(LivingEntity entity, Class<?> nmsClass) {
        return entity instanceof WaterMob || (nmsClass != null && NmsAccess.isA(nmsClass, NMS_WATER_MOB));
    }

    private static boolean isAmbient(LivingEntity entity, Class<?> nmsClass) {
        return entity instanceof Ambient || (nmsClass != null && NmsAccess.isA(nmsClass, NMS_AMBIENT));
    }

    private static String lowerOrNull(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private void logIntercept(String action, LivingEntity entity, CreatureSpawnEvent.SpawnReason reason, String ruleKey) {
        if (!SpawnBlockConfig.debug) {
            return;
        }
        String typeName = entity.getType() != null ? entity.getType().name() : "UNKNOWN";
        String key = NmsAccess.entityKey(entity);
        String zh = MobNames.resolve(key, typeName);
        StringBuilder sb = new StringBuilder();
        sb.append("[NeverLag] [SpawnBlock] ").append(action).append(": ")
                .append(zh).append(" [").append(key != null ? key : typeName).append("]")
                .append(" (原因: ").append(reason.name());
        if (ruleKey != null) {
            sb.append(", 规则: ").append(ruleKey);
        }
        sb.append(")");
        this.plugin.getLogger().info(sb.toString());
    }
}
