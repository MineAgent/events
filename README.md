# MC-event

把 Minecraft 里的游戏事件以 JSON 实时 POST 到本机 `127.0.0.1:3422`，供外部程序订阅。

| 目录 | 内容 |
|---|---|
| [`server/tcp_server.py`](server/tcp_server.py) | 接收端：只依赖 Python 标准库的 TCP/HTTP 服务，把事件按行打印到屏幕 |
| [`mod/`](mod/) | Fabric 模组 **MC Event Reporter 1.1.2**：**纯客户端**，Minecraft 26.2 / 26.3 + Fabric Loader 0.19.5+，不依赖 Fabric API、不需要服务端安装 |

## 1. 启动接收端

```sh
python3 server/tcp_server.py
# 事件服务器已启动：http://127.0.0.1:3422/  （Ctrl+C 退出）
```

可改监听地址/端口：`python3 server/tcp_server.py --host 127.0.0.1 --port 3422`。

每收到一个事件打印一行，`[类型]` 后面是完整 JSON：

```
[2026-11-06 23:49:07.652] 127.0.0.1:35688 [item_pickup] {"type":"item_pickup","time":206199,...}
```

没跑游戏时也可以先手工验证：

```sh
curl -X POST -H 'Content-Type: application/json' \
     --data '{"type":"hello","data":{}}' http://127.0.0.1:3422/
```

## 2. 构建、安装模组

```sh
cd mod
./gradlew build
cp build/libs/mc-event-1.1.2.jar ~/.minecraft/mods/
```

把 jar 放进 26.2 或 26.3 的 `26.x-Fabric` 实例的 `mods/` 后正常启动游戏即可。

26.3（Fabric Loader 0.20.1）实测：同一份 `mc-event-1.1.2.jar` 正常加载，`/evt` 挂在 3420 上；
触发聊天和 `/damage` 后接收端分别收到 `chat` / `player_hurt` JSON。Baritone 相关代码保持 1.19.0
编译期依赖不变，26.3 的 Baritone 适配不在本次范围。

目标地址默认是 `http://127.0.0.1:3422/`，需要改的话给 JVM 加参数
`-Dmcevent.endpoint=http://127.0.0.1:其它端口/`。

`mod/libs/baritone-api-fabric-1.19.0.jar` 只是 Baritone 的编译期依赖（`compileOnly`），
不会打进模组；运行时直接用 `mods/` 里的 Baritone，没装 Baritone 时相关功能自动跳过。

### 3420 `/evt/` 事件目录（可选）

装了 MGHttpdProvider 时会额外在共享服务上挂一个只读目录：

```sh
curl -s http://127.0.0.1:3420/evt/
```

每行是「事件 type + `data` 里的字段名」，用空格分隔，字段顺序与 JSON 里的顺序一致：

```
player_hurt damage_type attacker_type attacker health_before health_after absorption_before absorption_after health_lost absorption_lost damage_taken max_health hits_same_tick
item_pickup item count
chat message
hunger_change from to threshold
baritone_task_end result goal
dimension_change from to
player_death message damage_type attacker attacker_type
player_respawn dimension
```

这是静态目录，不返回事件内容；实际事件仍然 POST 到 `127.0.0.1:3422`。
没装 MGHttpdProvider 时该端点不存在，模组其它功能不受影响。

## 3. 事件格式

所有事件共用一个信封，`data` 里放各事件自己的字段：

```json
{
  "type": "item_pickup",
  "time": 206199,
  "position": {"x": 2.5, "y": 134.0, "z": 6.5},
  "data": {"item": "minecraft:stone", "count": 7}
}
```

| 信封字段 | 说明 |
|---|---|
| `type` | 事件名，见下表 |
| `time` | **游戏刻**（`level.getGameTime()`，世界创建以来的总 tick 数）；单调递增，方便排序 |
| `position` | 事件发生时玩家坐标（保留 2 位小数）；玩家不在世界时没有该字段 |
| `data` | 事件自己的字段 |

不包含玩家名、UUID、维度等重复信息；需要维度的只有 `dimension_change` 自己带 `from`/`to`。

| 事件 `type` | 触发时机 |
|---|---|
| `player_hurt` | 玩家受到伤害（包括被伤害吸收挡下、生命值没掉的情况） |
| `item_pickup` | 本地玩家捡起物品 |
| `chat` | 聊天栏收到任意一行文本 |
| `hunger_change` | 饥饿值向下跨过 18 / 6 / 0 |
| `baritone_task_end` | Baritone 任务结束（装了 Baritone 才会上报） |
| `dimension_change` | 客户端维度发生变化 |
| `player_death` | 本机玩家死亡 |
| `player_respawn` | 死亡后重生 |

### `player_hurt`（受伤）

| `data` 字段 | 说明 |
|---|---|
| `damage_type` | 伤害类型 ID，如 `minecraft:fall`、`minecraft:generic` |
| `attacker` / `attacker_type` | 造成伤害的实体名称 / 类型 ID，没有则为 `null` |
| `health_before` / `health_after` | 受伤前后的生命值（20 = 满血） |
| `absorption_before` / `absorption_after` | 受伤前后的伤害吸收值 |
| `health_lost` | 实际掉的生命值 |
| `absorption_lost` | 被伤害吸收吃掉的量 |
| `damage_taken` | `health_lost + absorption_lost` |
| `max_health` | 最大生命值 |
| `hits_same_tick` | 同一 tick 内的受伤次数 |

**「受伤」和「生命值降低」不是一回事**：事件以原版真正发出的伤害事件
（`ClientboundDamageEventPacket`）为触发条件，被伤害吸收全部吃掉或被盾牌挡下时生命值不变，
但仍会上报（`health_lost = 0`、`absorption_lost > 0`）。实测：

```json
// /damage @s 4
"health_before":17.5,"health_after":13.5,"health_lost":4.0,"damage_taken":4.0

// 有 4 点伤害吸收时 /damage @s 2：血没掉，但受伤事件照样上报
"health_before":13.5,"health_after":13.5,"absorption_before":4.0,"absorption_after":2.0,
"health_lost":0.0,"absorption_lost":2.0,"damage_taken":2.0
```

### `item_pickup`（获得物品）

只上报本地玩家自己捡起的物品，经验球忽略。

| `data` 字段 | 说明 |
|---|---|
| `item` | 物品 ID，如 `minecraft:stone` |
| `count` | 这次捡起的数量 |

```json
{"item":"minecraft:stone","count":7}
```

### `chat`（聊天栏输出）

聊天栏收到的每一行文本，包括玩家聊天、指令反馈、系统提示以及 Baritone / hint 等模组输出。

| `data` 字段 | 说明 |
|---|---|
| `message` | 这一行文本（已去掉颜色，保留玩家名等前缀） |

```json
{"type":"chat","time":208056,"position":{...},"data":{"message":"[Baritone] Going to: GoalBlock{x=4,y=134,z=6}"}}
```

### `hunger_change`（饥饿值跨阈值）

只在**向下**跨过关键阈值时上报，吃东西或普通变化不报：

| `threshold` | 条件 | 游戏内影响 |
|---|---|---|
| 18 | `from >= 18` 且 `to < 18` | 停止自然回血 |
| 6 | `from > 6` 且 `to <= 6` | 不能疾跑 |
| 0 | `from > 0` 且 `to == 0` | 开始饿掉血 |

| `data` 字段 | 说明 |
|---|---|
| `from` / `to` | 变化前后的饥饿值（0–20） |
| `threshold` | 本次跨过的阈值（18 / 6 / 0） |

```json
{"data":{"from":18,"to":17,"threshold":18}}
{"data":{"from":7,"to":6,"threshold":6}}
{"data":{"from":1,"to":0,"threshold":0}}
```

### `baritone_task_end`（Baritone 任务结束）

仅当安装了 Baritone（mod id `baritone`）时注册；没装时不会加载 Baritone 类。
任务进行时 Baritone 的当前目标非空，任务结束（到达 / 被取消 / 计算失败）后目标被清空，
模组在清空 2 tick 后上报一次。

| `data` 字段 | 说明 |
|---|---|
| `result` | `completed`（结束时玩家在目标内）或 `canceled`（没在目标内） |
| `goal` | 任务的 Baritone 目标描述 |

```json
{"data":{"result":"canceled","goal":"GoalBlock{x=-5,y=122,z=2}"}}
```

### `dimension_change`（切换维度）

每 tick 比较当前维度；从主世界进下界、从下界回主世界、进出模组维度都会各上报一条。
死亡后同维度重生不报。

| `data` 字段 | 说明 |
|---|---|
| `from` / `to` | 变化前后的维度 ID |

```json
{"data":{"from":"minecraft:overworld","to":"minecraft:the_nether"}}
```

### `player_death`（死亡）

收到本机玩家的死亡消息包时上报；`damage_type` / `attacker` / `attacker_type` 取自最近一次
`player_hurt`，没有受伤记录（例如 `/kill`）时为 `null`。

| `data` 字段 | 说明 |
|---|---|
| `message` | 原版死亡消息全文（含玩家名） |
| `damage_type` | 最近一次伤害类型 |
| `attacker` / `attacker_type` | 最近一次伤害的造成者名称 / 类型 |

```json
{"type":"player_death","time":217242,
 "data":{"message":"Steve 试图在熔岩里游泳","damage_type":"minecraft:lava","attacker":null,"attacker_type":null}}
```

### `player_respawn`（重生）

死亡后在重生界面点重生时上报，`dimension` 是重生后的维度。传送门跨维度不会触发它，
只会触发 `dimension_change`。

| `data` 字段 | 说明 |
|---|---|
| `dimension` | 重生后的维度 ID |

```json
{"type":"player_respawn","time":217442,"data":{"dimension":"minecraft:overworld"}}
```

## 4. 以后加新事件

接收端不用改（按 JSON 的 `type` 打印）。模组这边两步：

1. 新建一个 Tracker 类，组装 `Map<String, Object>` 后调用
   `Events.report("你的事件名", data)`；
2. 在 [`MinecraftMixin`](mod/src/main/java/com/mineagent/mcevent/mixin/MinecraftMixin.java)
   的 tick 里调用它，或另写 mixin 挂客户端原版包/事件。

`data` 支持字符串、数字、布尔、`Map`、`Iterable` 和 `null`，会被
[`Json`](mod/src/main/java/com/mineagent/mcevent/Json.java) 直接序列化。

## 5. 实现说明与坑

* **纯客户端**：只读客户端本来就会收到的原版网络包和客户端状态，不改服务端逻辑，
  单人、连原版服务器都能用。
* **客户端包会被处理两次**：第一次在网络线程，原版随后会把包重新排队到主线程。
  `ClientPacketListenerMixin` 用主线程判断跳过第一次，否则同一个事件会重复上报
  （开发时踩过：一次 `/damage` 收到两条）。
* **客户端拿不到伤害原始数值**：只能拿到生命值/伤害吸收的变化量，`damage_taken` 是
  结算后的实际损失；结算等生命值同步包（最多 10 tick）。完全被挡下的事件要等这 10 tick。
* **同一 tick 多次受伤**：按次数各上报一条，共享同一份生命值差值，用 `hits_same_tick` 标注。
* **Baritone 的 `PathEvent.AT_GOAL` 在这个版本里不可靠**（空闲时还会每 tick 刷 `CANCELED`），
  所以任务结束改用每 tick 观察 `getGoal()` 是否被清空来判定。
* 事件发送在独立守护线程里，接收端没开不会卡游戏，只在日志里记一次警告。
