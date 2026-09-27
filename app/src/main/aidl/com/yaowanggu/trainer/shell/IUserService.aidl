package com.yaowanggu.trainer.shell;

interface IUserService {

    /** 保留：Shizuku server 调用它来销毁服务 */
    void destroy() = 16777114;

    /** 返回 "uid=2000 pid=..." 之类的身份信息，用于确认以 shell 运行 */
    String id();

    /** 执行 shell 命令，返回 stdout（退出码非 0 时抛异常） */
    String exec(String command);

    /** 读整个文件 */
    byte[] readFile(String path);

    /** 覆写文件（原地写，保留 inode/owner） */
    void writeFile(String path, in byte[] data);

    /** 复制文件（用于 .bak 备份） */
    void copyFile(String from, String to);

    /** 列出 nfile*.save：每行 "槽位|大小|mtime|路径" */
    String[] listSaveSlots();

    /** 包名对应的进程是否在运行 */
    boolean isRunning(String packageName);
}
