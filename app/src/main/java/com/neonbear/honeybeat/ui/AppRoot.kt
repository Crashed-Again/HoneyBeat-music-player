package com.neonbear.honeybeat.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.neonbear.honeybeat.AppViewModel

@Composable
fun AppRoot(vm: AppViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openName by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.refresh()
    }
    LaunchedEffect(Unit) {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        launcher.launch(perms.toTypedArray())
    }

    BackHandler(showPlayer) { showPlayer = false }
    BackHandler(!showPlayer && tab == 0 && openName != null) { openName = null }

    Box(Modifier.fillMaxSize().background(HB.bg)) {
        when (tab) {
            0 -> {
                val pl = vm.playlists.firstOrNull { it.name == openName }
                if (pl != null) PlaylistScreen(vm, pl) { openName = null }
                else LibraryScreen(vm) { openName = it }
            }
            1 -> SearchScreen(vm)
            else -> OptionsScreen(vm)
        }

        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
            if (vm.player.hasMedia) {
                MiniPlayer(vm) { showPlayer = true }
                Spacer(Modifier.height(8.dp))
            }
            NavPill(tab) { tab = it; openName = null }
        }

        AnimatedVisibility(
            visible = showPlayer,
            enter = slideInVertically(tween(350)) { it },
            exit = slideOutVertically(tween(300)) { it }
        ) {
            PlayerScreen(vm) { showPlayer = false }
        }
    }
}
