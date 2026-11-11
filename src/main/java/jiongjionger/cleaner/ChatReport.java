package jiongjionger.cleaner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * 清理简报：游戏内发聊天组件（鼠标悬浮看每种生物的中文名与数量），控制台发纯文本。
 *
 * 发送分三级，按可靠性从高到低：
 * <ol>
 *   <li>{@code player.spigot().sendMessage(BaseComponent[])}：直接构造聊天包发给玩家，
 *       和 {@code Bukkit.broadcastMessage} 走的是同一条底层路径，只是内容为组件而非纯文本。
 *       {@code Server.broadcast(BaseComponent)} 是 1.8 才有的接口，1.7.10 只能逐个玩家发。</li>
 *   <li>{@code /tellraw <玩家名> <json>}：组件通道不可用时的退路。1.7.10 的 tellraw 只认单个玩家名，
 *       写 {@code @a} 会报「无法找到该玩家」，而指令系统仍返回成功，所以不能靠返回值判断。</li>
 *   <li>{@code Bukkit.broadcastMessage(纯文本)}：原生插件的垃圾清理就用这个方法，
 *       一次广播全体，但字符串带不了 hover，只能作最后兜底。</li>
 * </ol>
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
            int named, int tamed, int boss,
            Map<String, Integer> namedNames, Map<String, Integer> tamedNames, Map<String, Integer> bossNames) {
        String hover = buildHover(counts, total, elapsedMs, named, tamed, boss);
        String plain = plainLine(counts, total, named, tamed, boss);
        String json = buildJson(hover, counts, total, named, tamed, boss, namedNames, tamedNames, bossNames);
        if (MobClearConfig.debug) {
            plugin.getLogger().info("[NeverLag] 简报 JSON: " + json);
        }
        // 控制台永远留一行纯文本摘要；逐行明细只在 Debug 打开时打，免得把后台刷爆
        Bukkit.getConsoleSender().sendMessage(plain);
        if (MobClearConfig.debug) {
            for (String line : hover.split("\n")) {
                Bukkit.getConsoleSender().sendMessage("    " + line);
            }
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

        BaseComponent[] components = parse(plugin, json);
        if (components == null) {
            plainBroadcast(plugin, plain);
            return;
        }

        int sent = 0;
        int failed = 0;
        int viaSpigot = 0;
        int viaTellraw = 0;
        for (Player player : players) {
            int path = sendComponent(player, components, json);
            if (path >= 0) {
                sent++;
                if (path == 0) {
                    viaSpigot++;
                } else {
                    viaTellraw++;
                }
            } else {
                failed++;
                sendPlain(player, plain);
            }
        }
        if (sent == 0) {
            // 组件一条都没发出去，说明这条路在这个服务端不可用，退回原生插件的广播方式
            plainBroadcast(plugin, plain);
            return;
        }
        plugin.getLogger().info("[NeverLag] 简报播报：" + sent + " 名玩家（组件直发 " + viaSpigot
                + "，tellraw " + viaTellraw + "）"
                + (failed > 0 ? "，" + failed + " 名退回纯文本" : ""));
    }

    /**
     * JSON 转组件树。失败就说明这条路走不通，交给调用方退纯文本。
     */
    private static BaseComponent[] parse(Plugin plugin, String json) {
        try {
            return ComponentSerializer.parse(json);
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 简报 JSON 解析失败，改用纯文本播报: " + t);
            return null;
        }
    }

    /**
     * 直接把组件构造进聊天包发给玩家，不经过指令解析，也不经过 JSON 往返。
     * 失败时退回 tellraw（1.7.10 只认单个玩家名）。
     *
     * @return 0 = 组件直发，1 = tellraw，-1 = 两条路都不行
     */
    private static int sendComponent(Player player, BaseComponent[] components, String json) {
        try {
            player.spigot().sendMessage(components);
            return 0;
        } catch (Throwable ignored) {
            // 服务端没实现 Player#spigot() 或组件通道异常，试下一条路
        }
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tellraw " + player.getName() + " " + json);
            return 1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static void sendPlain(Player player, String plain) {
        try {
            player.sendMessage(plain);
        } catch (Throwable ignored) {
            // 玩家刚好退出，忽略
        }
    }

    /**
     * 和原生插件的垃圾清理播报完全一致的一条路：一次广播全体纯文本。
     * 抛异常只会发生在服务端关停过程中，吞掉即可。
     */
    private static void plainBroadcast(Plugin plugin, String plain) {
        try {
            int got = Bukkit.broadcastMessage(plain);
            plugin.getLogger().info("[NeverLag] 简报播报：" + got + " 名玩家（纯文本兜底）");
        } catch (Throwable t) {
            plugin.getLogger().warning("[NeverLag] 纯文本播报也失败了: " + t);
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
            int named, int tamed, int boss,
            Map<String, Integer> namedNames, Map<String, Integer> tamedNames, Map<String, Integer> bossNames) {
        List<String> parts = new ArrayList<String>();
        String message = color(MobClearConfig.message).replace("%COUNT%", String.valueOf(total));
        for (String[] part : splitLegacy(message)) {
            parts.add(component(part[1], part[0], hover));
        }
        // 每个分类计数各挂自己的名单，鼠标移到「敌对」「友好」上看的就是这一类
        for (MobType type : MobType.values()) {
            int count = sum(counts, type);
            if (count > 0) {
                parts.add(component(" · " + type.label() + " " + count, type.color(),
                        buildListHover(type.label(), count, counts.get(type))));
            }
        }
        // 三段「没吃的」同理，鼠标移到哪段就看哪一类
        addKeep(parts, buildListHover("戴着名牌", named, namedNames), MobClearConfig.namedMessage, "%NAMED%", named);
        addKeep(parts, buildListHover("家养的", tamed, tamedNames), MobClearConfig.tamedMessage, "%TAMED%", tamed);
        addKeep(parts, buildListHover("Boss", boss, bossNames), MobClearConfig.bossMessage, "%BOSS%", boss);

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

    /**
     * 一份「标题 + 名单」的悬浮文本，供分类计数和三段「没吃的」共用。
     * 没有名单时只给一行标题，避免悬停框空着。
     */
    private static String buildListHover(String title, int count, Map<String, Integer> names) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append(' ').append(count).append(" 只");
        if (names == null || names.isEmpty()) {
            return sb.toString();
        }
        List<Map.Entry<String, Integer>> entries = sorted(names);
        int shown = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (shown >= MAX_NAMES_PER_TYPE) {
                sb.append('\n').append("  …另有 ").append(entries.size() - shown).append(" 种");
                return sb.toString();
            }
            sb.append('\n').append("  ").append(entry.getKey()).append(": ").append(entry.getValue());
            shown++;
        }
        return sb.toString();
    }

    /**
     * 按数量从多到少排。数量相同时按名字排，避免每次播报顺序乱跳。
     */
    private static List<Map.Entry<String, Integer>> sorted(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                int c = b.getValue().compareTo(a.getValue());
                return c != 0 ? c : a.getKey().compareTo(b.getKey());
            }
        });
        return entries;
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
            List<Map.Entry<String, Integer>> entries = sorted(map);
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
            // 只给汇总，具体名单挂在消息后面三段各自的悬浮里
            sb.append("\n没吃的：\n");
            if (named > 0) {
                sb.append("  戴着名牌 ").append(named).append(" 只\n");
            }
            if (tamed > 0) {
                sb.append("  家养的 ").append(tamed).append(" 只\n");
            }
            if (boss > 0) {
                sb.append("  Boss ").append(boss).append(" 只\n");
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
