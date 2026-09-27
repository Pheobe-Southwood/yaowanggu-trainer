# 药王谷修改器 (yaowanggu-trainer)

《药王谷女修修炼手札》(com.hydrozoa.yyg) 的 Android 存档修改器。
**不需要 Root**，通过 [Shizuku](https://github.com/RikkaApps/Shizuku) 以 shell 权限读写游戏存档文件。

> 本项目与游戏作者 FelisWorks / Hydrozoa 无关联，仅供学习与研究使用。

## 功能

- **五官外貌修改（核心）**：脸型 / 眉毛 / 眼睛 / 嘴巴 / 鼻子 / 前发 / 后发 / 性格 / 痣 / 幼前发 / 幼后发，
  外加发色、瞳色、肤色的 HSV 数值。每个编号都带内置参考图（从社区教程图表提取）。
- **核心属性修改**：寿元、出生月/日、境界、灵根、灵气、武力等（高级字段折叠，未知字段标注风险）。
- **存档管理**：自动列出所有存档槽，默认选中最近修改的那个；写回前自动生成 `.bak` 备份。
- **诊断**：自动识别存档格式并展示解析结构，识别失败时可把结构发回开发者分析。
- **内置教程**：Shizuku 安装授权、关游戏、改脸、写回、还原全流程。

暂不支持灵石（货币为全局数据，不在角色 39 项字段表内，规避经济系统风险）。

## 使用流程

1. 安装 Shizuku 并启动（无线调试 / 已 root 设备）。
2. 打开本 App → 「存档」页 → 授权 Shizuku。
3. **完全退出游戏**（后台划掉）。
4. 点「刷新存档列表」→ 打开最近玩的存档。
5. 进入「五官」页调整数值（或「属性」页）。
6. 点「写回存档」→ 重开游戏查看。

## 构建

- 本地：`./gradlew testDebugUnitTest assembleDebug`
- 或推送到 GitHub，由 GitHub Actions 自动构建并上传 APK（本仓库约定：**不本地构建**，统一走 CI）。

环境：JDK 17、Android SDK 35、AGP 8.7.2、Kotlin 2.0.21。

## 技术说明

```
app/src/main/java/com/yaowanggu/trainer/
├── MainActivity.kt              # Shizuku 绑定 + Compose 入口
├── shizuku/ShizukuRepository.kt # shell 文件读写 / 存档列表
├── data/msgpack/MessagePack.kt  # 纯 Kotlin MessagePack 读写（保序、往返一致）
├── data/schema/                 # 外观 20 项 + 角色 39 项字段表
├── data/SaveAnalyzer.kt         # 存档格式识别 + 记录定位（树/裸二进制两种）
├── data/SaveEditor.kt           # 字段改写 + 重新序列化 / 原地打补丁
├── refs/RefCatalog.kt           # 参考图目录（assets/ref/catalog.json）
└── ui/                          # Compose UI（Material3）
```

### 关于存档格式

游戏为 Unity IL2CPP（仅 arm64），存档是 `Android/data/com.hydrozoa.yyg/files/nfile{0..31}.save`。
该文件的字节格式**尚未在真机上验证**：本 App 的 MessagePack 解析器为纯 Kotlin 实现，
若解析失败会退化为「按 little-endian int32 扫描」并匹配字段表特征。真机验证流程见
[docs/SAVE-FORMAT.md](docs/SAVE-FORMAT.md)。字段表来自社区 GG 修改器教程，与内存实测偏移一致，
见 [docs/FIELD-MAPS.md](docs/FIELD-MAPS.md)。

## 风险提示

- 修改前务必关闭游戏，否则改动会被游戏内存数据覆盖。
- 写回前会自动备份 `nfileX.save.bak`；出问题直接改名还原。
- 请自行做好存档备份，本项目对任何数据损坏概不负责。

## 许可

[MIT](LICENSE)
