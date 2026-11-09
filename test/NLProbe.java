package nlprobe;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Squid;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 实体清理验证探针，仅供测试服使用。
 * /nlprobe spawn &lt;n&gt; 在主世界出生点附近分散生成 n 头牛；
 * /nlprobe count 按 Bukkit 接口分类统计当前已加载世界内的活体实体。
 */
public class NLProbe extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("NLProbe enabled");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("[NLProbe] usage: /nlprobe spawn <n> | /nlprobe count");
            return true;
        }
        if ("spawn".equalsIgnoreCase(args[0])) {
            int n = args.length > 1 ? Integer.parseInt(args[1]) : 10;
            World world = Bukkit.getWorlds().get(0);
            Location base = world.getSpawnLocation();
            for (int i = 0; i < n; i++) {
                double angle = i * 2.399963;
                double radius = 8 + (i % 10) * 5;
                Location loc = base.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
                loc.setY(world.getHighestBlockYAt(loc) + 1);
                world.spawnEntity(loc, EntityType.COW);
            }
            sender.sendMessage("[NLProbe] spawned " + n + " cows in " + world.getName());
            return true;
        }
        if ("count".equalsIgnoreCase(args[0])) {
            int animals = 0, monsters = 0, squids = 0, villagers = 0, others = 0, total = 0;
            for (World world : Bukkit.getWorlds()) {
                for (LivingEntity entity : world.getLivingEntities()) {
                    total++;
                    if (entity instanceof Animals) {
                        animals++;
                    } else if (entity instanceof Monster) {
                        monsters++;
                    } else if (entity instanceof Squid) {
                        squids++;
                    } else if (entity instanceof Villager) {
                        villagers++;
                    } else {
                        others++;
                    }
                }
            }
            String msg = "[NLProbe] animals=" + animals + " monsters=" + monsters
                    + " squids=" + squids + " villagers=" + villagers
                    + " others=" + others + " total=" + total;
            sender.sendMessage(msg);
            getLogger().info(msg);
            return true;
        }
        sender.sendMessage("[NLProbe] unknown subcommand");
        return true;
    }
}
