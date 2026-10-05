package com.example.theiptv.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext

private const val NavigationVolume = 0.35f

@Composable
fun NavigationSoundHost(
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val audio = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (enabled) playNavigationSound(audio, event)
                false
            }
    ) {
        content()
    }
}

private fun playNavigationSound(audio: AudioManager, event: KeyEvent) {
    if (event.type != KeyEventType.KeyDown) return
    val repeatCount = (event.nativeKeyEvent as? android.view.KeyEvent)?.repeatCount ?: 0
    if (repeatCount > 0) return
    val effect = when (event.key) {
        Key.DirectionUp -> AudioManager.FX_FOCUS_NAVIGATION_UP
        Key.DirectionDown -> AudioManager.FX_FOCUS_NAVIGATION_DOWN
        Key.DirectionLeft -> AudioManager.FX_FOCUS_NAVIGATION_LEFT
        Key.DirectionRight -> AudioManager.FX_FOCUS_NAVIGATION_RIGHT
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape ->
            AudioManager.FX_KEY_CLICK
        else -> return
    }
    audio.playSoundEffect(effect, NavigationVolume)
}
