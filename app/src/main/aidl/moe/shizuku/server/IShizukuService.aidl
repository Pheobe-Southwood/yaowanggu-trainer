package moe.shizuku.server;

import moe.shizuku.server.IRemoteProcess;

/**
 * Shizuku 服务端 AIDL 的本地副本（只声明本 App 用到的方法）。
 * 包名/描述符/事务 id 必须与服务端完全一致。
 */
interface IShizukuService {

    int getVersion() = 2;

    int getUid() = 3;

    int checkPermission(String permission) = 4;

    IRemoteProcess newProcess(in String[] cmd, in String[] env, in String dir) = 7;

    String getSELinuxContext() = 8;
}
