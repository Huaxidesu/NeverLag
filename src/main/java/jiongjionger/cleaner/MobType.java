package jiongjionger.cleaner;

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
        Object handle = NmsAccess.handle(entity);
        return NmsAccess.isA(handle, "net.minecraft.entity.boss.IBossDisplayData");
    }

    /**
     * 返回 null 表示不参与清理（Boss、非生物、判定不出类别）。
     */
    public static MobType classify(LivingEntity entity) {
        Object handle = NmsAccess.handle(entity);
        if (handle != null) {
            if (NmsAccess.isA(handle, "net.minecraft.entity.boss.IBossDisplayData")) {
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
