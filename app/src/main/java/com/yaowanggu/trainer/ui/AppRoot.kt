package com.yaowanggu.trainer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import com.yaowanggu.trainer.ui.screens.AttrScreen
import com.yaowanggu.trainer.ui.screens.DiagScreen
import com.yaowanggu.trainer.ui.screens.FaceScreen
import com.yaowanggu.trainer.ui.screens.HelpScreen
import com.yaowanggu.trainer.ui.screens.HomeScreen

enum class Screen(val title: String) {
    HOME("存档"), FACE("五官外貌"), ATTR("属性"), DIAG("诊断"), HELP("教程")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: TrainerViewModel) {
    val state by vm.state.collectAsState()
    var screen by remember { mutableStateOf(Screen.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(m)
        vm.dismissMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("药王谷修改器 · ${screen.title}") },
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = { screen = Screen.HOME }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { screen = Screen.HELP }) {
                        Icon(Icons.Filled.Info, contentDescription = "使用教程")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen == Screen.HOME,
                    onClick = { screen = Screen.HOME },
                    icon = { Icon(Icons.Filled.Home, null) },
                    label = { Text("存档") },
                )
                NavigationBarItem(
                    selected = screen == Screen.FACE,
                    onClick = { screen = Screen.FACE },
                    enabled = state.faceReady,
                    icon = { Icon(Icons.Filled.Person, null) },
                    label = { Text("五官") },
                )
                NavigationBarItem(
                    selected = screen == Screen.ATTR,
                    onClick = { screen = Screen.ATTR },
                    enabled = state.persons.isNotEmpty() || state.rawCharOffset != null,
                    icon = { Icon(Icons.Filled.Settings, null) },
                    label = { Text("属性") },
                )
                NavigationBarItem(
                    selected = screen == Screen.DIAG,
                    onClick = { screen = Screen.DIAG },
                    icon = { Icon(Icons.Filled.List, null) },
                    label = { Text("诊断") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth()) {
                when (screen) {
                    Screen.HOME -> HomeScreen(vm, onOpenFace = { screen = Screen.FACE })
                    Screen.FACE -> FaceScreen(vm)
                    Screen.ATTR -> AttrScreen(vm)
                    Screen.DIAG -> DiagScreen(vm)
                    Screen.HELP -> HelpScreen()
                }
            }
        }
    }
}
