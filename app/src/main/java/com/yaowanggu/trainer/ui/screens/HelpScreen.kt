package com.yaowanggu.trainer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("药王谷修改器使用教程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("第一步：安装 Shizuku", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. 安装 Shizuku（Google Play / GitHub 搜索 Shizuku）。\n" +
                            "2. 安卓 11+：打开 Shizuku →「无线调试」→ 按提示在开发者选项里配对（需开启开发者选项与无线调试）。\n" +
                            "3. 安卓 8-10 机器本 App 不支持（minSdk 30）。\n" +
                            "4. 启动成功后通知栏会显示 Shizuku 正在运行。",
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("第二步：授权", style = MaterialTheme.typography.titleMedium)
                    Text("回到本 App，「存档」页显示 Shizuku 已就绪后，点击「授权 Shizuku」并允许。")
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("第三步：关闭游戏！", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "游戏只在自己保存时才会写存档文件。修改前必须完全退出药王谷（从后台划掉），" +
                            "否则你的修改可能被游戏的内存数据覆盖。"
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("第四步：改脸", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "1. 点「刷新存档列表」，排最上面的是最近玩的存档。\n" +
                            "2. 点「打开」→ 进入「五官」页。\n" +
                            "3. 调整脸型/眉毛/眼睛/嘴巴/鼻子/头发/颜色等编号（有参考图）。\n" +
                            "4. 点「写回存档」。App 会先自动备份为 .bak。\n" +
                            "5. 重新打开游戏查看效果。\n" +
                            "6. 不满意？把 .bak 改回原名即可还原。",
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("出现问题？", style = MaterialTheme.typography.titleMedium)
                    Text("到「诊断」页点「导出诊断包(zip)」：包含全部存档原始字节、解压后数据、诊断信息与运行日志；把 zip 发给开发者即可离线复现解析。也可点「复制诊断信息」快速反馈。")
                }
            }
        }
    }
}
