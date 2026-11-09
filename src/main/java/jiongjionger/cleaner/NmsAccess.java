package jiongjionger.cleaner;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 只读的 NMS 反射工具。
 *
 * 为什么用反射而不是直接 import：GTNH 服务端（Crucible/Thermos）运行期把类名反混淆成
 * net.minecraft.*，但**方法名和字段名仍是 SRG**（如 EntityList.getEntityString 实际叫
 * func_75621_b），版本之间还可能不同。这里只按“返回值/参数类型”找方法，不写死名字。
 *
 * 只做两件事：
 *   1. 取 Bukkit 实体对应的 NMS 句柄（CraftEntity#getHandle，CraftBukkit 自己的方法不会被重映射）
 *   2. 取 EntityList 里登记的实体键名（如 Cow、Thaumcraft.Wisp），用于查中文名
 */
public final class NmsAccess {

    private static final Map<Class<?>, Method> HANDLE_METHODS = new ConcurrentHashMap<Class<?>, Method>();
    private static final Object LOCK = new Object();

    private static volatile boolean keyResolved;
    private static Method keyMethod;

    private NmsAccess() {
    }

    public static Object handle(Object bukkitEntity) {
        if (bukkitEntity == null) {
            return null;
        }
        Class<?> clazz = bukkitEntity.getClass();
        Method method = HANDLE_METHODS.get(clazz);
        if (method == null) {
            try {
                method = clazz.getMethod("getHandle");
                method.setAccessible(true);
                HANDLE_METHODS.put(clazz, method);
            } catch (Throwable t) {
                return null;
            }
        }
        try {
            return method.invoke(bukkitEntity);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 判断 NMS 实体是否是某个类/接口（按类名逐级比较，兼容反混淆后的包名）。
     */
    public static boolean isA(Object nmsEntity, String className) {
        if (nmsEntity == null) {
            return false;
        }
        return isA(nmsEntity.getClass(), className, 0);
    }

    /**
     * 直接按类判断，省掉一次 getHandle。
     */
    public static boolean isA(Class<?> clazz, String className) {
        return isA(clazz, className, 0);
    }

    private static boolean isA(Class<?> clazz, String className, int depth) {
        if (clazz == null || depth > 32) {
            return false;
        }
        if (className.equals(clazz.getName())) {
            return true;
        }
        Class<?>[] interfaces = clazz.getInterfaces();
        for (int i = 0; i < interfaces.length; i++) {
            if (isA(interfaces[i], className, depth + 1)) {
                return true;
            }
        }
        return isA(clazz.getSuperclass(), className, depth + 1);
    }

    /**
     * EntityList 里登记的键名，取不到时返回 null。
     */
    public static String entityKey(Object bukkitEntity) {
        Object handle = handle(bukkitEntity);
        if (handle == null) {
            return null;
        }
        Method method = keyMethod();
        if (method == null) {
            return null;
        }
        try {
            Object value = method.invoke(null, handle);
            return value == null ? null : value.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method keyMethod() {
        if (keyResolved) {
            return keyMethod;
        }
        synchronized (LOCK) {
            if (keyResolved) {
                return keyMethod;
            }
            try {
                Class<?> entityCls = Class.forName("net.minecraft.entity.Entity");
                Class<?> entityList = Class.forName("net.minecraft.entity.EntityList");
                for (Method method : entityList.getMethods()) {
                    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    if (method.getReturnType() != String.class || method.getParameterTypes().length != 1) {
                        continue;
                    }
                    if (method.getParameterTypes()[0].isAssignableFrom(entityCls)) {
                        keyMethod = method;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
            keyResolved = true;
            return keyMethod;
        }
    }
}
