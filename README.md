# NeverLag 1.4.2（Minecraft 1.7.10 / GTNH 版）

> 本仓库是 [jiongjionger/NeverLag](https://github.com/jiongjionger/NeverLag) 的 fork。
> **默认分支 `legacy-1.3.0` 基于测试服实际运行的 1.3.0 成品 jar 反编译还原，再叠加本仓库的修复与新功能，
> 面向 GTNH（Minecraft 1.7.10 + Crucible）服务端。**
> 上游 `master` 的 2.0-SNAPSHOT 重写版源码完整保留在 `master` 分支；它的 cleaner / fixer
> 尚未接入 `onEnable()`，也没有 1.7.10 的 NMS 分支，不可直接部署。

## 1.4.2：修复游戏内不播报

1.4.0 / 1.4.1 里简报只写进了控制台，玩家聊天框什么都看不到。原因是 **1.7.10 的 `/tellraw`
  只接受单个玩家名，不认 `@a`**——`tellraw @a <json>` 会报「无法找到该玩家」，而指令系统
   仍然返回「已执行」，所以连纯文本兜底都不会触发。

现在的做法：

- 取在线玩家列表，**逐个玩家**发 `tellraw <玩家名> <json>`；
- 发送前先用服务端自己的 `ChatSerializer` 验一遍 JSON，解析失败就直接改发纯文本，
  保证「至少有一行播报」；
- 单个玩家发送异常时，只把这一名玩家降级成纯文本，不影响其他人；
- 控制台日志新增 `[NeverLag] 简报播报：N 名玩家`，一眼能看出播报到没到人。

诊断建议：`mobclear.yml` 里 `Debug: true` 会额外打出简报 JSON、`Broadcast` 关闭提示和
「没有玩家在线」提示。

## 1.4.1：默认「除 Boss 和命名过的，全清」

面向牲畜、水生生物动辄上万只的服务器。定时清理不给数量阈值，到点就按类别清一遍，
默认三类全开；只有 Boss、命名牌生物、驯服宠物会被留下，并在播报里说明「为什么没吃」。

### 清理什么

| 类别 | 判定方式 | 说明 |
|---|---|---|
| 敌对 | NMS `IMob`（Bukkit `Monster` 兜底） | 覆盖 `EntityMob`、史莱姆、恶魂，以及继承它们的 Mod 生物 |
| 友好 | `EntityAnimal`、`EntityWaterMob`、`EntityAmbientCreature`、村民 | 牛、猪、鸡、羊、鱿鱼、蝙蝠、村民 |
| 中立 | 其余 `EntityCreature`（`EntityGolem` 等） | 铁傀儡、雪傀儡、既非怪物也非动物的 Mod 生物 |

`mobclear.yml` 里 `Clean.Hostile / Friendly / Neutral` 默认全为 `true`，
`Exclude` 默认为空——**村民、铁傀儡也会被清**。要保住某类生物就往 `Exclude` 里加实体键。

以下对象**永远不清理**：

- 实现了 `net.minecraft.entity.boss.IBossDisplayData` 的 Boss（末影龙、凋灵、暮色九头蛇等）；
- `mobclear.yml` 的 `Boss` 列表里额外登记的 Mod Boss；
- **被命名牌命名过的生物**（`CustomName` 非空）；
- 已驯服的宠物/马（`Tameable#isTamed`）；
- 带 `NPC` / `MyPet` 元数据的实体。

画、展示框、拴绳结、掉落物、经验球不是 `LivingEntity`，`getLivingEntities()` 根本不会返回，
代码里另有一道 `Hanging` 判断兜底——**装饰物不会被这个功能碰到**。

**命名与敌对生物**：SpecialMobs 这类模组会给怪物自动起名（如「疾病缠身的 僵尸」），
如果一律按命名保留，大量变异怪会永久留下。因此 `ClearNamedHostile` 默认为 `true`：
**敌对生物带名字也照清**，友好/中立生物的命名始终保留。想让玩家命名的僵尸也留下，
把它改成 `false`。

判定优先用 NMS 基类/接口，所以 Mod 生物不需要单独适配。运行时拿不到 NMS 句柄时退回 Bukkit 接口。

### 中文名从哪来

`tools/gen_mobnames.py` 从 GTNH 整合包自带的汉化里提取 `entity.<键>.name` 条目：

1. 原版 1.7.10 的 `zh_CN.lang`（Minecraft 客户端 assets）
2. `mods/*.jar` 内 `assets/<modid>/lang/zh_CN.lang`
3. `config/txloader/forceload/*/lang/zh_CN.lang`（GTNH 官方中文包）
4. `____gtnhoverridenames_zhcn`（GTNH 对原版名词的修正，最高优先级）

生成 `src/main/resources/mobnames_zh_cn.txt`（本版 987 条，含 modid 前缀与去掉前缀两种写法），
打进 jar，运行时只查表。查不到时依次回退到「去掉 modid 的键」「Bukkit 实体类型名」。

例如 SpecialMobs 的 `SpecialMobs.HungryZombie` 没有单独汉化，会回退到 Bukkit 类型 `ZOMBIE` → 「僵尸」；
有汉化的则显示「饥饿僵尸」。1.4.1 实测中 `火焰僵尸`、`瘟疫僵尸`、`野蛮僵尸`、`巨大僵尸`
这些 SpecialMobs 变体都能正确显示并清理。

### 清理简报

清理完成后全服播报，鼠标悬停看按类别分组的「中文名: 数量」明细：

```
[大肥猫] 这次成功吃了 226 只怪，嗝~ · 敌对 12 · 友好 214 （命名的 2 只太硬，卡牙，这次没吃）（5 只 Boss，打不过） [明细]
    悬停 →
    清理完成，共 226 只

    友好 214 只
      牛: 180
      羊: 22
      村民: 8
      …

    没吃的：
      命名牌 2 只：太硬，卡牙
      Boss 5 只：打不过
    耗时 186 毫秒
```

「没吃的」会随数量变化，没有就不显示；文案可在 `mobclear.yml` 用
`NamedMessage` / `TamedMessage` / `BossMessage` 改写，置空字符串即关闭。
悬浮明细每类最多列 30 种，多出来的写成「…另有 N 种」，避免上万只生物时把聊天包撑大。

游戏内用 `/tellraw` 发 JSON（1.7.10 原生支持 `hoverEvent`），控制台同时打印纯文本明细。
不自己拼数据包，是为了避开 Spigot / Cauldron 之间数据包类名不一致的问题。

### 指令

```
/neverlag clear all          清理敌对 + 友好 + 中立全部生物（不受配置类别开关限制）
/neverlag clear now          按 mobclear.yml 的类别立即清理一次并播报
/neverlag clear <实体ID>     只清理该种生物
```

`<实体ID>` 支持四种写法（不区分大小写）：

- EntityList 键：`zombie`、`Thaumcraft.Wisp`
- 去掉 modid 的键：`wisp`、`hungryzombie`
- Bukkit 类型名：`THAUMCRAFT_WISP`、`ZOMBIE`
- 中文名：`僵尸`、`牛`、`精灵`

Tab 补全可列出 `all`、`now` 与实体键。玩家需 OP；控制台/RCON 可直接执行。

### 异步与性能

实体删除必须在主线程做，所以拆成三段：

1. **主线程**：取一次各世界 `getLivingEntities()` 快照（只复制引用，并预热实体类型缓存）
2. **异步线程**：判定类别、查中文名，挑出待清理列表
3. **主线程**：每 tick 最多删除 `BatchSize` 只，且最多占用 `TimeBudgetMs` 毫秒，
   清完后才播报

判定与查表这类真正耗时的部分不占主线程；删除被切成小批，不会出现一次性删几千只导致的卡顿。

清理范围是**当前已加载区块内的实体**。没有玩家的区块本来就不 tick，实体也不会造成负担，
等玩家过去、区块加载后下一轮会照常清掉。

### 配置 `mobclear.yml`

```yaml
Enable: true            # 定时清理总开关；开启后旧 ClearEntity 定时任务自动让位
Delay: 1800             # 清理间隔（秒）= 半小时，到点就清，没有数量阈值
Clean:
  Hostile: true         # 敌对
  Friendly: true        # 友好（动物、鱿鱼、蝙蝠、村民）
  Neutral: true         # 中立（铁傀儡等）
BatchSize: 200          # 每 tick 最多删除多少只
TimeBudgetMs: 4         # 每 tick 最多占用多少毫秒
ExtraHostile: []        # 额外归类，填 EntityList 键
ExtraFriendly: []
ExtraNeutral: []
Exclude: []             # 永不清理，默认不排除（村民也会被清）
Boss:                   # IBossDisplayData 之外还要保护的 Boss
  - TwilightForest.Hydra
  - ...
Broadcast: true
Debug: false            # 把简报 JSON 打进控制台
ClearNamedHostile: true # 敌对生物带名字也清；改成 false 则命名僵尸也保留
Message: '&b[NeverLag] &a本次清理了 &c%COUNT% &a只生物'
NamedMessage: '&7（命名的 &f%NAMED% &7只太硬，卡牙，这次没吃）'
TamedMessage: '&7（&f%TAMED% &7只驯服宠物，下不去口）'
BossMessage: '&7（&f%BOSS% &7只 Boss，打不过）'
```

改完执行 `/neverlag reload` 生效，不用重启。

## 1.3.1 修复了什么

**问题：开启实体清理后，动物（以及其它实体）怎么配置都不会被定时清理。**

根因是 1.3.0 里的两处逻辑缺陷，二者叠加使清理任务在常见配置下根本不会执行：

1. **配置项写错字段**：`NeverLag.loadConfig()` 中
   `ClearEntity = config.getBoolean("ClearEntity.OnlyClearWhenLimit");`
   把「仅到达数量上限才清理」的值写进了 `ClearEntity` 总开关，而真正表示该语义的
   `ClearLimit` 字段始终为 `null`。`onEnable()` 又用 `ClearEntity` 决定是否注册清理任务，
   于是 `OnlyClearWhenLimit: false` 会让整个实体清理任务**不被注册**。
   现改为写入 `ClearLimit`。
2. **阈值判断无条件生效**：`ClearEntity.clean_run()` 开头恒执行
   `if (num < ClearLimitNum) return;`，完全忽略 `OnlyClearWhenLimit` 开关。
   即使任务被注册，配置 `OnlyClearWhenLimit: false`（期望每周期都清理）时，
   仍要等全服活体数达到 `Limit`（默认 1200）才会执行一次。
   现改为仅在 `ClearLimit` 为真时才做阈值判断。

附带修复：

- `AntiPMM` 直接引用 `Material.SLIME_BLOCK`（该枚举常量自 1.8 才有），在 1.7.10 上
  触发活塞事件会抛 `NoSuchFieldError`；改为按方块名比较。
- `ClearEntity.Delay` 非正值时调度周期为 0 会导致插件启动失败，现设下限 1 秒。

## 构建

需要 JDK 8 与服务端的 `Crucible.jar`、`plugins/ProtocolLib.jar` 作为编译期依赖。
在 Git Bash 中执行：

```bash
./build.sh
# 可用环境变量覆盖路径：
# SERVER_DIR=... JAVAC=... JAR=... ./build.sh
```

产物为 `build/NeverLag1.4.1.jar`。

重新生成中文名表（需要一份 GTNH 客户端实例）：

```bash
python tools/gen_mobnames.py \
  --instance "<实例>/.minecraft" \
  --vanilla  "<客户端>/assets/objects/xx/<zh_CN.lang 的 hash 文件>" \
  --out      src/main/resources/mobnames_zh_cn.txt
```

## 部署

1. 停止服务端。
2. 把旧 jar 移出 `plugins/` 根目录，**不要保留两份同 modId 的 jar**。
3. 放入 `build/NeverLag1.4.1.jar`，启动服务端；首次启动会生成 `plugins/NeverLag/mobclear.yml`。
4. 日志应出现 `Enabling NeverLag v1.4.1`、`已加载 987 条生物中文名`、`生物清理已开启，每 1800 秒一次`。
5. 旧 `config.yml` 里的 `ClearEntity.Enable: true` 会在新清理开启时自动让位，
   日志会打印「旧的 ClearEntity 定时任务不再注册」。

## 验证结果

环境：本机测试服 `gtnh1/server2`（Crucible 1.7.10 + Java 26，GTNH 2.9.0），RCON 端口 25576，
探针插件统计实体类型与自定义名。

| 场景 | 结果 |
|---|---|
| 混合实体 `clear all` | 22 个活体实体中清掉 15 只：敌对 7（火焰僵尸 2、僵尸 2、瘟疫僵尸 1、巨大僵尸 1、野蛮僵尸 1）、友好 8（牛 3、村民 3、羊 2），耗时 126 ms |
| 命名牌生物 | 命名牛 `NameCow`、命名羊保留；命名僵尸 `PlayerPet` 按 `ClearNamedHostile: true` 被清 |
| Boss | 5 只暮色九头蛇全部保留，播报「5 只 Boss，打不过」 |
| 画 / 展示框 | `summon Painting`、`summon ItemFrame` 生成后不受影响（非 LivingEntity，扫描列表里根本没有） |
| 村民 | `Exclude: []` 下正常清理，3 只全清 |
| 中文名 | SpecialMobs 变体显示「火焰僵尸 / 瘟疫僵尸 / 野蛮僵尸 / 巨大僵尸」，Mod 生物无需单独适配 |
| 规模测试 | 一次 `clear all` 清 226 只，总耗时 186 ms；期间 RCON 往返最小 1 / 平均 7 / 最大 63 ms，无 `Can't keep up` 日志 |
| 重复执行 | 再执行一次得到「没有符合条件的生物，命名牌保留 2 只，Boss 保留 5 只」 |

启动与清理期间未出现与 NeverLag 相关的异常。

## 分支

| 分支 | 内容 |
|---|---|
| `legacy-1.3.0`（默认） | 1.3.0 反编译还原源码 + 1.3.1 修复 + 1.4.x 生物清理 |
| `master` | fork 自带的上游 2.0-SNAPSHOT 重写版源码，未改动 |

## 来源与许可

- 原始插件作者：jiongjionger，仓库 <https://github.com/jiongjionger/NeverLag>。
- 1.3.0 未随仓库发布源码，本分支源码由 CFR 0.152 反编译成品 jar 还原后整理，
  仅用于修复与自行部署。
- 生物中文名取自 GTNH 整合包自带的汉化文件，仅用于显示。
- 沿用上游 GPL-3.0，许可全文见 `LICENSE.md`。
