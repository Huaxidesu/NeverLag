# NeverLag 1.3.1（Minecraft 1.7.10 修复版）

> 本仓库是 [jiongjionger/NeverLag](https://github.com/jiongjionger/NeverLag) 的 fork。
> **当前默认分支 `legacy-1.3.0` 基于测试服实际运行的 1.3.0 成品 jar 反编译还原并修复，
> 不是上游 `master` 的 2.0-SNAPSHOT 重写版。** 上游 2.0 源码完整保留在 `master` 分支，
> 其 cleaner / fixer 尚未接入 `onEnable()`，不可直接用于 1.7.10 服务端。

## 本次修复了什么

**问题：开启实体清理后，动物（以及其它实体）怎么配置都不会被定时清理。**

根因是 1.3.0 里的两处逻辑缺陷，二者叠加使清理任务在常见配置下根本不会执行：

1. **配置项写错字段**：`NeverLag.loadConfig()` 中
   `ClearEntity = config.getBoolean("ClearEntity.OnlyClearWhenLimit");`
   把「仅到达数量上限才清理」的值写进了 `ClearEntity` 总开关，而真正表示该语义的
   `ClearLimit` 字段始终为 `null`。`onEnable()` 又用 `ClearEntity` 决定是否注册清理任务，
   于是 `OnlyClearWhenLimit: false` 会让整个实体清理任务**不被注册**；
   反过来 `Enable: false` + `OnlyClearWhenLimit: true` 反而会启动任务。
   现改为写入 `ClearLimit`。
2. **阈值判断无条件生效**：`ClearEntity.clean_run()` 开头恒执行
   `if (num < ClearLimitNum) return;`，完全忽略 `OnlyClearWhenLimit` 开关。
   即使任务被注册，配置 `OnlyClearWhenLimit: false`（期望每周期都清理）时，
   仍要等全服活体数达到 `Limit`（默认 1200）才会执行一次。
   现改为仅在 `ClearLimit` 为真时才做阈值判断。

附带修复两处会影响 1.7.10 运行的问题：

- `AntiPMM` 直接引用 `Material.SLIME_BLOCK`（该枚举常量自 1.8 才有），在 1.7.10 上
  触发活塞事件会抛 `NoSuchFieldError`；改为按方块名比较，1.7.10 上自然不命中。
- `ClearEntity.Delay` 非正值时调度周期为 0 会导致插件启动失败，现设下限 1 秒。
- 版本号由 `1.3.0` 升至 `1.3.1`，其余功能与配置格式保持不变。

## 配置说明

`ClearEntity.Delay` 的单位是**秒**，调度周期为 `Delay * 20` ticks。
测试服曾配置为 `144000`（40 小时），这会让清理几乎不触发，属于配置问题而非代码缺陷；
验证时请调至较小值（如 `60`）。

```yaml
ClearEntity:
  Enable: true                # 实体清理总开关
  Delay: 1200                 # 清理周期，单位秒
  OnlyClearWhenLimit: false   # false = 每周期都清理；true = 活体数达到 Limit 才清理
  Limit: 1200                 # 仅 OnlyClearWhenLimit: true 时生效
  Animals: true               # 是否清理动物
  Monster: true               # 是否清理怪物
  Squid: true                 # 是否清理鱿鱼
  Villager: false             # 是否清理村民
```

清理时会跳过带 `NPC` 或 `MyPet` 元数据的实体。动物判定使用 Bukkit 的
`org.bukkit.entity.Animals` 接口；村民、鱿鱼不属于该接口，分别由各自开关控制。

## 构建

需要 JDK 8 与服务端的 `Crucible.jar`、`plugins/ProtocolLib.jar` 作为编译期依赖。
在 Git Bash 中执行：

```bash
./build.sh
# 可用环境变量覆盖路径：
# SERVER_DIR=... JAVAC=... JAR=... ./build.sh
```

产物为 `build/NeverLag1.3.1.jar`。

## 部署

1. 停止服务端。
2. 把旧的 `plugins/NeverLag1.3.0.jar` 移出 `plugins/` 根目录（或改名为 `.jar.bak`），
   **不要保留两份同名插件的 jar**，否则 Bukkit 会重复加载。
3. 放入 `build/NeverLag1.3.1.jar`，按需调整 `plugins/NeverLag/config.yml`，启动服务端。
4. 日志应出现 `Enabling NeverLag v1.3.1`。

## 分支

| 分支 | 内容 |
|---|---|
| `legacy-1.3.0`（默认） | 1.3.0 反编译还原源码 + 本修复，面向 1.7.10 |
| `master` | fork 自带的上游 2.0-SNAPSHOT 重写版源码，未改动 |

## 验证结果

环境：本机测试服 `gtnh1/server2`（Crucible 1.7.10 + Java 26，GTNH 2.9.0），RCON 端口 25576。
用 `test/NLProbe.java` 编译的探针插件统计 `instanceof Animals` 的活体数量：

```bash
javac -source 8 -target 8 -cp <服务端>/Crucible.jar -d classes test/NLProbe.java
# 把 test/plugin.yml 与编译产物打成 NLProbe.jar 放入 plugins/，重启后：
# /nlprobe count、/nlprobe spawn 20
```

两次实验使用**完全相同**的配置：`ClearEntity.Enable=true`、`Delay=60`、
`OnlyClearWhenLimit=false`、`Animals=true`、`Monster=true`；为避免其它模块干扰，
临时关闭 `AntiFarmMob` 与 `MobLimit`。只更换 `plugins/` 里的 jar。

| 实验 | 插件版本 | 基线 | 刷 20 头牛后 | 等待 75 秒后 | 结论 |
|---|---|---|---|---|---|
| 修复版 | 1.3.1 | 48 | 68 | **4** | 定时清理执行，动物被清除（剩余为清理后自然生成） |
| 对照 | 1.3.0 | 0 | 20 | **20** | 清理任务根本没注册，动物一只未清 |

日志摘录：

```
[20:58:52] Enabling NeverLag v1.3.1
[20:59:47] [NLProbe] animals=68 monsters=0 squids=0 villagers=0 others=0 total=68
[21:01:03] [NLProbe] animals=4  monsters=0 squids=0 villagers=0 others=0 total=4

[21:11:50] Enabling NeverLag v1.3.0
[21:12:08] [NLProbe] animals=20 monsters=0 squids=0 villagers=0 others=0 total=20
[21:13:24] [NLProbe] animals=20 monsters=0 squids=0 villagers=0 others=0 total=20
```

启动与运行期间未出现与 NeverLag 相关的异常。测试结束后 server2 已恢复为
「修复版 jar + 原始配置 + 停止」状态；如需复验，请自行把 `Animals` 设为 `true`、
`Delay` 调至较小值（原始配置分别为 `false` 与 `144000` 秒）。

## 来源与许可

- 原始插件作者：jiongjionger，仓库 <https://github.com/jiongjionger/NeverLag>。
- 1.3.0 未随仓库发布源码，本分支源码由 CFR 0.152 反编译成品 jar 还原后整理，
  仅用于修复与自行部署。
- 沿用上游 GPL-3.0，许可全文见 `LICENSE.md`。
