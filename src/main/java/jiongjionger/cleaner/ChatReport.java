package jiongjionger.cleaner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 清理简报：游戏内用 tellraw 发 JSON（鼠标悬浮看每种生物的中文名与数量），控制台发纯文本。
 *
 * 走 /tellraw 而不是自己拼数据包，是为了不碰各服务端实现不一样的数据包类名；
 * tellraw 在 1.7.10 就支持 hoverEvent，JSON 由服务端自己解析。
 *
 * 注意：1.7.10 的 tellraw 只认单个玩家名，写 {@code @a} 会报「无法找到该玩家」
 * （而且指令系统仍然返回成功，不会触发纯文本兜底），所以这里逐个玩家单独发。
 */
public final class ChatReport {

    /**
     * 悬浮明细里每个类别最多列多少种。
     * 上万个生物时名字条目可能上百，聊天包不能无限撑大。
     */
    private static final int MAX_NAMES_PER_TYPE = 30;

    private ChatReport() {
    }

    public static void broadcast(Plugin plugin, Map<MobType, Map<String, Integer>> counts, int total, long elapsedMs,
            int named, int tamed, int boss) {
        String hover = buildHover(counts, total, elapsedMs, named, tamed, boss);
        String plain = plainLine(counts, total, named, tamed, boss);
        String json = buildJson(hover, counts, total, named, tamed, boss);
        if (MobClearConfig.debug) {
            plugin.getLogger().info("[NeverLag] 简报 JSON: " + json);
        }
        // 控制台永远打纯文本，附带完整明细（游戏内悬浮用的是同一份文字）
        Bukkit.getConsoleSender().sendMessage(plain);
        for (String line : hover.split("\n")) {
            Bukkit.getConsoleSender().sendMessage("    " + line);
        }
        if (!MobClearConfig.broadcast) {
            if (MobClearConfig.debug) {
                plugin.getLogger().info("[NeverLag] Broadcast 为 false，跳过游戏内播报");
            }
            return;
        }
        List<Player> players = new ArrayList<Player>(Bukkit.getOnlinePlayers());
        if (players.isEmpty()) {
            if (MobClearConfig.debug) {
                plugin.getLogger().info("[NeverLag] 没有玩家在线，简报只写控制台");
            }
            return;
        }
        if (!jsonParsable(plugin, json)) {
            // JSON 本身有问题就别浪费一轮 tellraw，直接退回纯文本
            for (Player player : players) {
                player.sendMessage(plain);
            }
            return;
        }
        int sent = 0;
        int failed = 0;
        for (Player player : players) {
            if (tellraw(player, json)) {
                sent++;
            } else {
                failed++;
                player.sendMessage(plain);
            }
        }
        plugin.getLogger().info("[NeverLag] 简报播报：" + sent + " 名玩家"
                + (failed > 0 ? "，" + failed + " 名退回纯文本" : ""));
    }

    /**
     * 1.7.10 的 tellraw 只接受单个玩家名，必须逐个发；指令出错时 Bukkit 仍可能返回 true，
     * 所以这里只把抛异常当作失败。
     */
    private static boolean tellraw(Player player, String json) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 用服务端自己的解析器（net.minecraft.util.ChatSerializer）验一遍 JSON。
     * 类名对不上、反射不可用时一律放行，不因为校验不了就不播报。
     */
    private static boolean jsonParsable(Plugin plugin, String json) {
        try {
            Class<?> serializer = Class.forName("net.minecraft.util.ChatSerializer");
            for (Method method : serializer.getMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers()) || params.length != 1
                        || params[0] != String.class || method.getReturnType() == void.class) {
                    continue;
                }
                method.setAccessible(true);
                method.invoke(null, json);
                return true;
            }
            return true;
        } catch (InvocationTargetException e) {
            plugin.getLogger().warning("[NeverLag] 简报 JSON 解析失败，改发纯文本: " + e.getCause());
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    public static String plainLine(Map<MobType, Map<String, Integer>> counts, int total,
            int named, int tamed, int boss) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.stripColor(color(MobClearConfig.message)).replace("%COUNT%", String.valueOf(total)));
        for (MobType type : MobType.values()) {
            int c = sum(counts, type);
            if (c > 0) {
                sb.append(" · ").append(type.label()).append(' ').append(c);
            }
        }
        appendKeep(sb, MobClearConfig.namedMessage, "%NAMED%", named);
        appendKeep(sb, MobClearConfig.tamedMessage, "%TAMED%", tamed);
        appendKeep(sb, MobClearConfig.bossMessage, "%BOSS%", boss);
        return sb.toString();
    }

    /**
     * 追加「没吃的」说明，模板为空或数量为 0 时不显示。
     */
    private static void appendKeep(StringBuilder sb, String template, String token, int count) {
        if (count <= 0 || template == null || template.trim().length() == 0) {
            return;
        }
        sb.append(' ').append(ChatColor.stripColor(color(template)).replace(token, String.valueOf(count)));
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private static int sum(Map<MobType, Map<String, Integer>> counts, MobType type) {
        Map<String, Integer> map = counts.get(type);
        if (map == null) {
            return 0;
        }
        int total = 0;
        for (Integer value : map.values()) {
            total += value == null ? 0 : value;
        }
        return total;
    }

    private static String buildJson(String hover, Map<MobType, Map<String, Integer>> counts, int total,
            int named, int tamed, int boss) {
        List<String> parts = new ArrayList<String>();
        String message = color(MobClearConfig.message).replace("%COUNT%", String.valueOf(total));
        for (String[] part : splitLegacy(message)) {
            parts.add(component(part[1], part[0], hover));
        }
        for (MobType type : MobType.values()) {
            int count = sum(counts, type);
            if (count > 0) {
                parts.add(component(" · " + type.label() + " " + count, type.color(), hover));
            }
        }
        addKeep(parts, hover, MobClearConfig.namedMessage, "%NAMED%", named);
        addKeep(parts, hover, MobClearConfig.tamedMessage, "%TAMED%", tamed);
        addKeep(parts, hover, MobClearConfig.bossMessage, "%BOSS%", boss);
        for (String[] part : splitLegacy(color(" &8[明细]"))) {
            parts.add(component(part[1], part[0], hover));
        }

        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"text\":\"\",\"extra\":[");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(parts.get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void addKeep(List<String> parts, String hover, String template, String token, int count) {
        if (count <= 0 || template == null || template.trim().length() == 0) {
            return;
        }
        String text = color(template).replace(token, String.valueOf(count));
        boolean first = true;
        for (String[] part : splitLegacy(text)) {
            parts.add(component((first ? " " : "") + part[1], part[0], hover));
            first = false;
        }
    }

    private static String component(String text, String color, String hover) {
        StringBuilder sb = new StringBuilder(text.length() + 64);
        sb.append("{\"text\":\"").append(escape(text)).append('"');
        if (color != null) {
            sb.append(",\"color\":\"").append(color).append('"');
        }
        if (hover != null) {
            sb.append(",\"hoverEvent\":{\"action\":\"show_text\",\"value\":\"").append(escape(hover)).append("\"}");
        }
        sb.append('}');
        return sb.toString();
    }

    /**
     * 把 §/& 颜色代码拆成 [颜色名, 文本] 片段，供 JSON 组件使用。
     */
    private static List<String[]> splitLegacy(String text) {
        List<String[]> parts = new ArrayList<String[]>();
        StringBuilder current = new StringBuilder();
        String colorName = null;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                String name = colorName(code);
                if (name != null) {
                    if (current.length() > 0) {
                        parts.add(new String[]{colorName, current.toString()});
                        current.setLength(0);
                    }
                    colorName = name;
                    i++;
                    continue;
                }
                if ("klmno".indexOf(code) >= 0) {
                    i++;
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            parts.add(new String[]{colorName, current.toString()});
        }
        return parts;
    }

    private static String colorName(char code) {
        switch (code) {
            case '0': return "black";
            case '1': return "dark_blue";
            case '2': return "dark_green";
            case '3': return "dark_aqua";
            case '4': return "dark_red";
            case '5': return "dark_purple";
            case '6': return "gold";
            case '7': return "gray";
            case '8': return "dark_gray";
            case '9': return "blue";
            case 'a': return "green";
            case 'b': return "aqua";
            case 'c': return "red";
            case 'd': return "light_purple";
            case 'e': return "yellow";
            case 'f': return "white";
            default: return null;
        }
    }

    private static String buildHover(Map<MobType, Map<String, Integer>> counts, int total, long elapsedMs,
            int named, int tamed, int boss) {
        StringBuilder sb = new StringBuilder();
        sb.append("清理完成，共 ").append(total).append(" 只\n");
        for (MobType type : MobType.values()) {
            Map<String, Integer> map = counts.get(type);
            if (map == null || map.isEmpty()) {
                continue;
            }
            sb.append('\n').append(type.label()).append(' ').append(sum(counts, type)).append(" 只\n");
            List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
            Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
                @Override
                public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                    return b.getValue().compareTo(a.getValue());
                }
            });
            int shown = 0;
            for (Map.Entry<String, Integer> entry : entries) {
                if (shown >= MAX_NAMES_PER_TYPE) {
                    sb.append("  …另有 ").append(entries.size() - shown).append(" 种\n");
                    break;
                }
                sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
                shown++;
            }
        }
        if (named > 0 || tamed > 0 || boss > 0) {
            sb.append("\n没吃的：\n");
            if (named > 0) {
                sb.append("  命名牌 ").append(named).append(" 只：太硬，卡牙\n");
            }
            if (tamed > 0) {
                sb.append("  驯服宠物 ").append(tamed).append(" 只：下不去口\n");
            }
            if (boss > 0) {
                sb.append("  Boss ").append(boss).append(" 只：打不过\n");
            }
        }
        sb.append("耗时 ").append(elapsedMs).append(" 毫秒");
        return sb.toString();
    }

    private static String escape(String text) {
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
