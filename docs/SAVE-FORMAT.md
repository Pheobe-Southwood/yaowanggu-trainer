# SAVE-FORMAT — 存档格式验证流程与当前状态

## 文件位置与模块模型（2026-09-28 真机诊断包实证，v0.1.4）

```
/storage/emulated/0/Android/data/com.hydrozoa.yyg/files/nfile{0..31}.save
```

- **nfile0..31 不是 32 个存档槽，而是同一份存档的 32 个数据模块**：
  所有文件 mtime 完全相同（游戏批量重写全部模块），并自带 `nfileN_backup.save` / `nfileN_backup1.save` 轮转备份。
- 模块形态（实测）：
  | 模块 | 大小 | 容器 | 内容 |
  |---|---|---|---|
  | nfile0/1/3/4 等 | 76B~1.4KB | **base64(加密二进制)** | 高熵不可解析（疑似 AES），设置/状态类 |
  | nfile2/5..18 | 12B | base64（内容全部相同） | 8 字节密文 |
  | nfile19 | 17KB | plain msgpack map(1527) | int → 6 元 int 数组（查找表） |
  | nfile24/25 | 2~3B | JSON 文本 | `[0]` / `{}` |
  | **nfile30** | 29KB | **zlib(msgpack)**（`78 01` 头，解压后 59KB） | **角色/外观模块** |
  | nfile31 | 1KB | plain msgpack map(347) | 角色 ID → 小数组（索引表） |
- **nfile30 结构**：msgpack 根数组 = **347 条变长 int 记录**（每角色一条，记录索引 i ↔ 角色 ID i+1）。
  每条记录尾部附近有 **20 字段五官窗口**，`窗口[0] == 记录索引+1`（347/347 验证通过）；
  窗口之前是角色前缀数据（含疑似灵气/武力大数，如 1604748/412500）。
  FaceSchema 字段表与真实数值范围完全吻合（脸型/眉/眼/嘴 ≤12、鼻 ≤8、性格 ≤9、痣 ≤3、色相 ≤360、饱和/明暗 ≤400）。
- 访问需要 Shizuku（Android 11+ 无 root），见 `ADR-0001`。

## 角色属性字段校准（v0.1.6，2026-10-01 诊断包 + 游戏面板截图）

记录布局（nfile30，每角色一条变长 int 记录）：
`前缀固定区(0..~20) | 变长列表区 | … | 尾块20 | 五官20 | 后缀`
（变长列表区使绝对下标漂移，故属性一律用**相对五官窗口起点 faceStart 的偏移**锚定。）

面板 ground truth（主角 叶星华：寿元 10/60、灵气 0/0、武力 1/1、凡人、第1年1月1日、药王谷内门弟子）：
| 字段 | 锚点 | 验证 |
|---|---|---|
| 寿元当前 | faceStart−63 | 新档 3 条凡人记录 (10,60,0,ID) 模式；旧档 23/60、22/60、65/60 同位 |
| 寿元上限 | faceStart−62 | 同上 |
| 灵气 | faceStart−20 | 尾块首项；旧档大数 14185061/7375000/9001422 同位；新主角=0 ✓ |
| 武力 | faceStart−19 | 尾块次项；旧档 3162500/6552306/952000 同位；新主角=1 ✓ |

未映射（不开放修改）：灵玉(灵石)、境界、突破几率、灵根、出生年月、所在地/门派/职位 ——
本轮数据中无法唯一锚定（全零/枚举冲突），待带时间差的双诊断包差分分析。
实现：`data/CharMap.kt`（faceRel 偏移表）+ AttrScreen 仅渲染已校准字段。

## 颜色映射实证（v0.1.5，社区色表 color.jpg 13 组标注样本）

- 游戏「色相」与标准 HSV 色相**镜像**：std = (360 − game) mod 360。
  佐证：13→鲜红(347°)、22→粉(338°)、52→品红(308°)、72→淡紫(288°)、128→深藏青(232°)、146→蓝(214°)、236→绿(124°)、331→亮黄(29°)、360→棕(0°)。
- 饱和/明暗：0..200 → 0..1（200 满，400 钳制）。佐证：13;200;147=饱和鲜红、128;54;50=深藏青、222;0;400=白。
- 肤色仅存饱和/明暗两字段，色相为游戏内固定基色（预览取 25°）。
- 实现：`data/GameColor.kt`（纯函数 + 单测）；FaceScreen 色块另有描边与 #RRGGBB 显示。

## 写回安全协议（v0.1.5）

事故背景：v0.1.4 首次写回后游戏「检测不到存档」（推断为游戏进程未退、与写盘互踩产生截断模块）；第二次（游戏已杀）正常。
协议：
1. 写回前 `pidof com.hydrozoa.yyg` 非空 → **硬阻断**，提示完全退出游戏。
2. 备份到游戏目录之外：`/sdcard/yaowanggu-trainer/backup/nfileN-<ts>.save.bak`。
3. 原子写：`<path>.dsh-tmp` 写入 → 回读字节比对 → `SaveCodec.decode` 容器自检 → `mv -f`（同 fs rename 原子）→ 回读最终文件再验证；失败清理 tmp、原文件不动。
4. 游戏目录残留（`*.dsh-tmp` / `nfile*.save.bak`）扫描提示 + 一键移到备份目录。
5. 首页「恢复此备份」一键回滚；诊断包含备份路径/残留列表/逐步写回日志。

## v0.1.3 两个致命 bug（v0.1.4 已修复，留档）

1. **写回容器错误**：nfile30 是 zlib 容器，v0.1.3 写回 plain msgpack → 游戏 Inflate 失败 →
   回退 `nfile30_backup.save` → 游戏内毫无变化；退出时游戏把模块重写回 zlib。
   （「plain msgpack 官方兼容」只适用于 MessagePack 内置 LZ4 压缩，不适用于游戏自加的 zlib 外层。）
2. **bestFace 选中垃圾窗口**：record0 前缀的全零窗口评分 24（满分）且按遍历序先于真脸窗口 →
   用户改的 5 项全部写进记录前缀标志位。
   修复：`PersonFinder` ID 锚定定位 + 全零/无 ID/无色相窗口降权 + 角色选择器。

## 已确认事实（来自游戏 APK 静态分析，v0.1.3）

- `global-metadata.dat` 含 `MessagePackCompression`、`Lz4Block`、`Lz4BlockArray`、
  `MessagePack.Formatters`、`MessagePack.dll`、`SaveData`、`nfile0..nfile31` 字符串
  → 存档由 **MessagePack-CSharp** 序列化，且引用了 **LZ4 压缩**选项。
- MessagePack-CSharp 官方源码（`MessagePackSerializer.cs` / `ReservedExtensionTypeCodes.cs`）给出磁盘格式：
  - **Lz4Block**：整文件 = msgpack `ext(type=99)`，payload = `[msgpack int32 解压后长度][LZ4 block]`；
  - **Lz4BlockArray**：整文件 = msgpack `array`，首元素 = `ext(type=98)`（payload 为各 chunk 解压长度），
    后续 N 个 `bin` 为各 chunk 的 LZ4 block；
  - 序列化结果小于 `CompressionMinLength` 时**原样不压缩**；
  - 反序列化 `TryDecompress()`：头部不是 ext99/array98 时**按 plain msgpack 直接读**
    → **写回不压缩的 plain msgpack 官方兼容，无需 LZ4 编码器**。
- v0.1.2 之前「31 个槽全部检测不到外观数据」的根因即此：`tryParse` 只接受根为 Map/Arr，
  ext99 根是 Ext → null → raw 扫描压缩字节 → 0 命中。

## App 的处理策略（v0.1.3）

| 步骤 | 代码 | 说明 |
|------|------|------|
| 1 | `SaveCodec.decode(bytes)` | 容器嗅探：ext99 → array98 → plain → gzip/zlib → 头部跳过(1..32) → unknown |
| 2 | `Lz4.decompressBlock` | 纯 Kotlin LZ4 block 解码 |
| 3 | `PersonFinder.findPersons(tree)` | **ID 锚定**定位角色记录与五官窗口（主路径） |
| 4 | `SaveAnalyzer.findRecordsInTree()` | 通用评分兜底（全零/无 ID/无色相窗口降权） |
| 5 | `SaveAnalyzer.findRecordsInRaw()` | innerBytes 的 int32 LE 扫描兜底（format = `<container>+raw`） |
| 6 | `SaveEditor.apply()` | 树模式：整树重序列化；raw 模式：patch innerBytes |
| 7 | **`SaveCodec.encode(container, newInner, originalBytes)`** | **按原容器重新打包**（zlib → Deflater BEST_SPEED 产出 `78 01`；gzip；lz4 → 纯字面量块；msgpack@+N 保留前缀）+ decode 回读自检，失败中止写入 |
| 8 | 写回前 `cp nfileX.save nfileX.save.bak` + 写后回读验证 | 手机侧通过 Shizuku 完成 |
| 9 | 模块扫描 + 自动打开外观模块 | mtime 无意义（批量写）；按「persons>0」选择，列表按编号排序并显示容器/角色数徽标 |
| 10 | 「导出诊断包(zip)」 | 全部模块原始字节 + inner + diagnostics.txt（模块表/选中角色/前缀）+ applog.txt |

## 真机验证步骤

1. 启动 Shizuku → 打开 App → 授权 → 完全退出游戏。
2. 「刷新存档列表」→ 自动扫描全部模块并打开「外观模块」（nfile30，带「外观模块 · N 个角色」徽标）。
3. 看首页提示：`已识别外观模块（zlib+msgpack）· 347 个角色，默认选中 ID 1`。
4. 「五官」页顶部**角色选择器**：ID 1 通常是玩家主角；不确定时对照游戏内灵气/武力数值选「提示值」最接近的记录。
5. 改一项 → 「写回存档」→ 提示「已写回（zlib+msgpack，备份 …，回读验证 N 个角色）」→ 重开游戏确认生效。
6. 角色前缀字段（寿元/灵石等）尚未映射：「诊断」页有**记录前缀查看器**，把索引-数值对照游戏内数值发回开发者，即可支持 CharSchema 修改。
7. 任何异常 → 「诊断」页「导出诊断包(zip)」发回开发者。

## 已知风险

- 游戏对 nfile30 有 `_backup`/`_backup1` 轮转：读档失败会静默回退备份（v0.1.3 即栽在这里）；v0.1.4 写回后回读验证 + 容器自检兜底。
- base64 加密模块（nfile0..4 等）暂不支持修改（五官不需要）；如需灵石等字段，先经前缀查看器映射 nfile30 记录前缀。
- 若游戏版本更新改变记录布局，PersonFinder 的 ID 锚定会失效并回退通用评分——导出诊断包可快速定位。
