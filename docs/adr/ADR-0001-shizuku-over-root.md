# ADR-0001 使用 Shizuku 而非 Root

## 状态

已接受

## 背景

Android 11+ 起，普通应用无法访问其他应用的 `Android/data/<pkg>/files/`
（Scoped Storage）。游戏存档就在该目录下。用户设备未 root。
GG 修改器教程本质上是运行时改内存，教程操作繁琐，且无法做成独立 App。

## 决策

前置软件使用 [Shizuku](https://github.com/RikkaApps/Shizuku)：

- 用户通过 adb / 无线调试授权一次，Shizuku 提供 `newProcess` 执行 shell（uid 2000）命令。
- uid 2000 属于 `sdcard_rw`/`everybody` 组，可读写 `Android/data/<pkg>/` 下的文件。
- App 通过 `sh -c 'cat path' / 'cat > path'` 读写字节，不依赖 root，不需要 StorageManager。
- 依赖：`dev.rikka.shizuku:api:13.1.5` + `provider:13.1.5`，manifest 声明
  `rikka.shizuku.ShizukuProvider`（authority = `${applicationId}.shizuku`）。

## 后果

- 首次使用要求用户安装 Shizuku，App 内置引导教程（内置简明教程为 v1 需求）。
- 每次授权周期对应 Shizuku 的进程生命周期；App 需在 onResume 重新检查 binder/permission。
- 无法关闭游戏——只能检测（`pidof`）并提醒用户先退出游戏再读写。
- 操作全部同步 shell 命令实现，简单可靠，无需绑定 Shizuku 的用户/服务 API。
