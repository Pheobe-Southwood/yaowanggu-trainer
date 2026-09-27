# SAVE-FORMAT — 存档格式验证流程与当前状态

## 文件位置

```
/storage/emulated/0/Android/data/com.hydrozoa.yyg/files/nfile{0..31}.save
```

- 槽位 0–31，同一目录下共 32 个文件；最近修改的通常是玩家实际使用的槽（App 刷新后自动打开）。
- 访问需要 Shizuku（Android 11+ 无 root），见 `ADR-0001`。

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
| 1 | `SaveCodec.decode(bytes)` | 容器嗅探：plain → ext99 → array98 → gzip/zlib → 头部跳过(1..32) → unknown |
| 2 | `Lz4.decompressBlock` | 纯 Kotlin LZ4 block 解码（只解码不编码） |
| 3 | `SaveAnalyzer.findRecordsInTree()` | 在解压后的树中找 int 顺序数组，按特征评分定位外观(20)/角色(39) |
| 4 | `SaveAnalyzer.findRecordsInRaw()` | innerBytes 的 int32 LE 扫描兜底（format = `<container>+raw`） |
| 5 | `SaveEditor.apply()` | 树模式：整树重新序列化为 **plain msgpack**；raw 模式：patch innerBytes 后同样以 plain 写回 |
| 6 | 写回前 `cp nfileX.save nfileX.save.bak` | 手机侧通过 Shizuku 完成 |
| 7 | 「导出诊断包(zip)」 | 全部槽位原始字节 + inner + diagnostics.txt + applog.txt，SAF 保存后发回开发者 |

## 真机验证步骤

1. 启动 Shizuku → 打开 App → 授权 → 完全退出游戏。
2. 「刷新存档列表」→ 自动打开最近修改的槽（带「推荐 · 游戏在用」徽标）。
3. 看首页提示：
   - `已识别外观记录（msgpack+lz4block / lz4blockarray / msgpack）` → 到「五官」页看数值是否有意义（脸型 0–12、眼睛 1–10、生日 1–12/1–31）。
   - `未识别结构化外观，已定位到疑似二进制偏移` → raw 模式，同样观数值。
   - `未能自动定位外观数据` → 「诊断」页点「导出诊断包(zip)」发回开发者。
4. 进入「五官」页改一项 → 「写回存档」→ 重开游戏确认生效。
5. 若字段位置偏移（例如五官错位成装备），导出诊断包发回来调整 `FaceSchema`/`CharSchema` 的偏移与评分。

## 已知风险

- 若游戏对存档有 checksum/签名，任何写入都会导致读档失败——真机第一次写入就是答案（.bak 可还原）。
- 若字段在文件里是小端 int32 数组但结构是「记录长度前缀」，raw 模式仍有效。
- 若存档另有加密（非 MessagePack 容器），导出包中的 container/HEX 可一轮内定位。
