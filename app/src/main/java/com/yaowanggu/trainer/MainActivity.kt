package com.yaowanggu.trainer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.yaowanggu.trainer.ui.AppRoot
import com.yaowanggu.trainer.ui.TrainerViewModel
import com.yaowanggu.trainer.ui.theme.YaowangguTrainerTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val vm: TrainerViewModel by viewModels()

    private val binderReceived = Shizuku.OnBinderReceivedListener { vm.refreshShizuku() }
    private val binderDead = Shizuku.OnBinderDeadListener { vm.refreshShizuku() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        setContent {
            YaowangguTrainerTheme {
                AppRoot(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshShizuku()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        super.onDestroy()
    }
}
