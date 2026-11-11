package jiongjionger.cleaner;

import java.util.Locale;

import org.bukkit.entity.Animals;
import org.bukkit.entity.Bat;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Villager;

/**
 * 清理类别。
 *
 * 判定优先用 NMS 的基类/接口（能覆盖 Mod 生物），NMS 拿不到时退回 Bukkit 接口：
 *   敌对：IMob（EntityMob、史莱姆、恶魂、苦力怕……）
 *   友好：EntityAnimal、EntityWaterMob（鱿鱼）、EntityAmbientCreature（蝙蝠）、村民
 *   中立：其余 EntityCreature（铁傀儡、雪傀儡、部分 Mod 生物）
 * Boss（IBossDisplayData）不参与清理。
 */
public enum MobType {

    HOSTILE("敌对", "red"),
    FRIENDLY("友好", "green"),
    NEUTRAL("中立", "yellow");

    private final String label;
    private final String color;

    MobType(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() {
        return label;
    }

    public String color() {
        return color;
    }

    public static boolean isBoss(LivingEntity entity) {
        return isBoss(NmsAccess.handle(entity));
    }

    /**
     * Boss 判定，三条独立规则，命中任意一条即保护：
     *
     * <ol>
     *   <li>{@code IBossDisplayData}——1.7.10 的头顶血条只认这个接口，屏幕上方有血条的一定命中；</li>
     *   <li>{@code IEntityMultiPart}——末影龙、九头蛇、娜迦、混沌守卫这类多部位实体，全是 Boss 级；</li>
     *   <li>类名（含父类）里带 {@code Boss}——覆盖没实现血条接口、名字却明摆着是 Boss 的模组生物。</li>
     * </ol>
     *
     * 第三条可以用 {@code ProtectBossNamed} 关掉，前两条是硬判定。
     */
    public static boolean isBoss(Object handle) {
        if (handle == null) {
            return false;
        }
        Class<?> clazz = handle.getClass();
        if (NmsAccess.isA(clazz, "net.minecraft.entity.boss.IBossDisplayData")) {
            return true;
        }
        if (NmsAccess.isA(clazz, "net.minecraft.entity.IEntityMultiPart")) {
            return true;
        }
        return MobClearConfig.protectBossNamed && hasBossInName(clazz, 0);
    }

    private static boolean hasBossInName(Class<?> clazz, int depth) {
        if (clazz == null || depth > 24) {
            return false;
        }
        String simple = clazz.getSimpleName();
        if (simple != null && simple.toLowerCase(Locale.ROOT).contains("boss")) {
            return true;
        }
        return hasBossInName(clazz.getSuperclass(), depth + 1);
    }

    /**
     * 神秘时代的傀儡与旅行箱。这两类实体是玩家的生产设施，被清理会造成实际损失，
     * 因此与配置无关地固定保留；按基类判断，附属模组继承同一基类的实体一并覆盖。
     */
    public static boolean isThaumcraftGolem(LivingEntity entity) {
        return isThaumcraftGolem(NmsAccess.handle(entity));
    }

    public static boolean isThaumcraftGolem(Object handle) {
        if (handle == null) {
            return false;
        }
        return NmsAccess.isA(handle.getClass(), "thaumcraft.common.entities.golems.EntityGolemBase");
    }

    /**
     * 精英怪：由 InfernalMobs 在持久 NBT 里打的标记识别，不看名字。
     * 这个模组的名字在客户端现渲染，服务端读不到，只有 NBT 能可靠区分。
     */
    public static boolean isInfernal(LivingEntity entity) {
        return NmsAccess.isInfernal(NmsAccess.handle(entity));
    }

    /**
     * 返回 null 表示不参与清理（Boss、非生物、判定不出类别）。
     */
    public static MobType classify(LivingEntity entity) {
        Object handle = NmsAccess.handle(entity);
        if (handle != null) {
            if (isBoss(handle)) {
                return null;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.monster.IMob")) {
                return HOSTILE;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.passive.EntityAnimal")) {
                return FRIENDLY;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.passive.EntityWaterMob")) {
                return FRIENDLY;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.passive.EntityAmbientCreature")) {
                return FRIENDLY;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.passive.EntityVillager")) {
                return FRIENDLY;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.passive.EntityGolem")) {
                return NEUTRAL;
            }
            if (NmsAccess.isA(handle, "net.minecraft.entity.EntityCreature")) {
                return NEUTRAL;
            }
        }
        if (entity instanceof Monster) {
            return HOSTILE;
        }
        if (entity instanceof Animals || entity instanceof Squid || entity instanceof Villager || entity instanceof Bat) {
            return FRIENDLY;
        }
        if (entity instanceof Golem) {
            return NEUTRAL;
        }
        return null;
    }
}
