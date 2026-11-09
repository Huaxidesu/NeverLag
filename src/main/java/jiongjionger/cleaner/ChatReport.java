package jiongjionger.cleaner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;

/**
 * 清理简报：游戏内用 tellraw 发 JSON（鼠标悬浮看每种生物的中文名与数量），控制台发纯文本。
 *
 * 走 /tellraw 而不是自己拼数据包，是为了不碰各服务端实现不一样的数据包类名；
 * tellraw 在 1.7.10 就支持 hoverEvent，JSON 由服务端自己解析。
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
        String json = buildJson(hover, counts, total, named, tamed, boss);
        if (MobClearConfig.debug) {
            plugin.getLogger().info("[NeverLag] 简报 JSON: " + json);
        }
        if (!MobClearConfig.broadcast) {
            return;
        }
        boolean delivered = false;
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            try {
                delivered = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw @a " + json);
            } catch (Throwable t) {
                plugin.getLogger().warning("[NeverLag] 发送清理简报失败，退回纯文本: " + t);
            }
        }
        if (!delivered) {
            Bukkit.broadcastMessage(plainLine(counts, total, named, tamed, boss));
        }
        Bukkit.getConsoleSender().sendMessage(plainLine(counts, total, named, tamed, boss));
        for (String line : hover.split("\n")) {
            Bukkit.getConsoleSender().sendMessage("    " + line);
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
        parts.add(component(" §8[明细]", null, hover));

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
