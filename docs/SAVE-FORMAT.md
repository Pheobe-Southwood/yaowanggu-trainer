# SAVE-FORMAT — 存档格式验证流程与当前状态

## 文件位置

```
/storage/emulated/0/Android/data/com.hydrozoa.yyg/files/nfile{0..31}.save
```

- 槽位 0–31，同一目录下共 32 个文件；最近修改的通常是玩家实际使用的槽。
- 访问需要 Shizuku（Android 11+ 无 root），见 `ADR-0001`。

## 目前结论（未经真机验证）

- 游戏为 Unity IL2CPP，arm64 专用。manifest 与全局元数据中未出现 MessagePack.dll？
  实际上 APK 的 `assets/bin/Data/` 中确实含 MessagePack.dll，
  且报错字符串中有 MessagePack 相关信息，因此**推测**存档为 MessagePack 序列化
  （游戏运行时用 `MessagePackSerializer.Serialize<存档对象>` 写入）。
- 但社区提供的所有教程均为 GG 运行时内存修改，没有任何人 dump 过磁盘文件。

## App 的处理策略

| 步骤 | 代码 | 说明 |
|------|------|------|
| 1 | `MessagePack.tryParse(bytes)` | 必须是「完整解析到文件末尾」才认为成功，避免误判 |
| 2 | `SaveAnalyzer.findRecordsInTree()` | 在树中找 int 顺序数组，按特征评分定位外观(20)/角色(39) |
| 3 | `SaveAnalyzer.findRecordsInRaw()` | int32 LE 扫描 + 同样评分（format = raw-int32） |
| 4 | `SaveEditor.apply()` | 树模式：整树重新序列化；raw 模式：原地 patch 4 字节 |
| 5 | 写回前 `cp nfileX.save nfileX.save.bak` | 手机侧通过 Shizuku 完成 |

## 真机验证步骤（交付给用户的小白鼠测试）

1. 启动 Shizuku → 打开 App → 授权 → 完全退出游戏。
2. 「刷新存档列表」→ 打开最近修改的槽位。
3. 看首页提示：
   - `已识别外观记录（messagepack）` → 到「五官」页看数值是否有意义（脸型 0–12、眼睛 1–10、生日 1–12/1–31）。
   - `未识别结构化外观，已定位到疑似二进制偏移` → raw 模式，同样观数值。
   - `未能自动定位外观数据` → 去「诊断」页把 header HEX / 结构预览截图上传 issue。
4. 进入「五官」页改一项 → 「写回存档」→ 重开游戏确认生效。
5. 若字段位置偏移（例如五官错位成装备），把诊断页截图发回来调整 `FaceSchema`/`CharSchema` 的偏移与评分。

## 已知风险

- 若游戏对存档有 checksum/签名，任何写入都会导致读档失败——真机第一次写入就是答案。
- 若字段在文件里是小端 int32 数组但结构是「记录长度前缀」，raw 模式仍有效。
- 若存档在运行时做了压缩/加密，需要逆向全局元数据中的存档类序列化（工作量较大）。
