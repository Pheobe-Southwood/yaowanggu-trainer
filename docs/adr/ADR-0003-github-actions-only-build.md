# ADR-0003 只通过 GitHub Actions 构建

## 状态

已接受（用户明确要求）

## 背景

本地环境有 JDK 17/25 与 Android SDK，但用户不希望在本机执行 Gradle 构建；
且仓库包含约 1.3GB 的参考 APK，本机构建会产生大量缓存。

## 决策

- 仓库不包含本地构建产物，`gradlew` 仅用于 CI 与本地备选。
- 所有验证（单元测试 + APK）通过 `.github/workflows/android.yml` 完成：
  ubuntu-latest + JDK 17 + setup-android + `./gradlew testDebugUnitTest assembleDebug assembleRelease`，
  产物以 artifact 上传。
- 触发器：push/PR main、workflow_dispatch。

## 后果

- 每次提交都能得到可测的 debug APK 与未签名 release APK。
- Kotlin 代码必须格外小心：编写时没有本地编译反馈，第一轮编译即 CI。
- 单元测试（纯 JVM）覆盖 MessagePack 往返与记录定位，确保 CI 尽早失败。
