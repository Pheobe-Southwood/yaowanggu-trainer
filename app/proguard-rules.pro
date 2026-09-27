# Keep Shizuku provider/aidl classes
-keep class dev.rikka.shizuku.** { *; }
-keep interface dev.rikka.shizuku.** { *; }

# Shizuku 用户服务：由 Shizuku 服务进程按类名实例化，名字和构造器不能混淆
-keep class com.yaowanggu.trainer.shell.** { *; }
-keep interface com.yaowanggu.trainer.shell.** { *; }
