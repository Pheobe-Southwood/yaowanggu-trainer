package moe.shizuku.server;

// 与方法顺序必须和服务端 IRemoteProcess.aidl 一致，使默认分配的事务 id 1..8 对得上
interface IRemoteProcess {

    ParcelFileDescriptor getOutputStream();

    ParcelFileDescriptor getInputStream();

    ParcelFileDescriptor getErrorStream();

    int waitFor();

    int exitValue();

    void destroy();

    boolean alive();

    boolean waitForTimeout(long timeout, String unit);
}
