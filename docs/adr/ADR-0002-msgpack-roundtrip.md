# ADR-0002 MessagePack 往返读写 + 裸数据兜底

## 状态

已接受

## 背景

`nfile{0..31}.save` 的二进制格式没有公开资料，社区教程全部是运行时改内存。
APK 内打包 `MessagePack.dll`，`global-metadata`/异常字符串中也有 msgpack 字样，
推断存档为 MessagePack 序列化（int 为 key 的字典），但**未经真机验证**。

## 决策

分两层实现 `SaveAnalyzer` / `SaveEditor`：

1. **树解析（优先）**：自写纯 Kotlin MessagePack 阅读器/写入器（约 500 行），
   完整支持 map/array/int/float/str/bin/ext，保持 key 顺序与二进制字段原样；
   解析后用字段表特征（生日 1–12/1–31、性别 1–4、灵根 0/1、8 灵根、颜色 0–400…）
   在树中定位外观/角色记录；改写用整树重新序列化，非结构化字段完全不动。
2. **裸数据兜底（format = raw-int32）**：若解析失败，按 little-endian int32 线性扫描，
   按同样的特征评分定位记录，改写时只原地 patch 对应 4 字节。

诊断页把格式、header HEX、记录位置、结构预览直接给用户看，可截图回传修正映射。

## 后果

- 解析成功时改写最安全（不破坏其他字段）。
- 真机首测即可判定格式是否符合预期：如果解析失败，需要追加其他容器格式
  （protobuf/自研长度前缀）支持，而非改动 UI。
- 树解析失败≠无解：raw 模式仍可改脸/属性，但只能 4 字节对齐地改。
- 自写 MessagePack 是主要代码风险，用 JVM 单元测试覆盖往返与已知向量。
