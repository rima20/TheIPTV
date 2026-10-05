package com.example.theiptv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch

fun KeyEvent.isHeldRepeat(): Boolean {
    val native = nativeKeyEvent
    return native is AndroidKeyEvent && native.repeatCount > 0
}

sealed class ListWrapAction {
    data object Pass : ListWrapAction()
    data object Consume : ListWrapAction()
    data class Jump(val index: Int) : ListWrapAction()
}

fun listWrapAction(
    key: Key,
    repeating: Boolean,
    index: Int,
    count: Int,
    columns: Int = 1,
    wrapVertical: Boolean = true,
    wrapHorizontal: Boolean = false,
): ListWrapAction {
    if (count <= 0) return ListWrapAction.Pass
    val cols = columns.coerceAtLeast(1)
    val last = count - 1
    val atFirstRow = index < cols
    val lastRowStart = (last / cols) * cols
    val atLastRow = index >= lastRowStart
    fun edge(enabled: Boolean, atEdge: Boolean, target: Int): ListWrapAction {
        if (!enabled || !atEdge) return ListWrapAction.Pass
        if (count <= 1 || repeating) return ListWrapAction.Consume
        return ListWrapAction.Jump(target)
    }
    return when (key) {
        Key.DirectionDown -> edge(wrapVertical, atLastRow, (index % cols).coerceAtMost(last))
        Key.DirectionUp -> edge(
            wrapVertical,
            atFirstRow,
            (lastRowStart + (index % cols)).coerceAtMost(last),
        )
        Key.DirectionRight -> edge(wrapHorizontal, index == last, 0)
        Key.DirectionLeft -> edge(wrapHorizontal, index == 0, last)
        else -> ListWrapAction.Pass
    }
}

@Stable
class ListWrapState {
    val first = FocusRequester()
    val last = FocusRequester()
    val jump = FocusRequester()
    var jumpIndex by mutableStateOf<Int?>(null)
        private set

    fun requester(index: Int, count: Int): FocusRequester? {
        val extra = jumpIndex
        if (extra != null && index == extra) return jump
        if (index == 0) return first
        if (count > 1 && index == count - 1) return last
        return null
    }

    fun prepareJump(target: Int, count: Int) {
        jumpIndex = if (target == 0 || (count > 1 && target == count - 1)) null else target
    }

    fun clearJump() {
        jumpIndex = null
    }
}

@Composable
fun rememberListWrapState(): ListWrapState = remember { ListWrapState() }

@Composable
fun Modifier.wrapListNavigation(
    index: Int,
    count: Int,
    state: ListWrapState,
    scrollTo: suspend (Int) -> Unit,
    columns: Int = 1,
    wrapVertical: Boolean = true,
    wrapHorizontal: Boolean = false,
): Modifier {
    val scope = rememberCoroutineScope()
    val scrollToState = rememberUpdatedState(scrollTo)
    val countState = rememberUpdatedState(count)
    val columnsState = rememberUpdatedState(columns)
    val requester = state.requester(index, count)
    return this
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (
                val action = listWrapAction(
                    key = event.key,
                    repeating = event.isHeldRepeat(),
                    index = index,
                    count = countState.value,
                    columns = columnsState.value,
                    wrapVertical = wrapVertical,
                    wrapHorizontal = wrapHorizontal,
                )
            ) {
                ListWrapAction.Pass -> false
                ListWrapAction.Consume -> true
                is ListWrapAction.Jump -> {
                    val target = action.index
                    val total = countState.value
                    scope.launch {
                        state.prepareJump(target, total)
                        scrollToState.value(target)
                        withFrameNanos { }
                        withFrameNanos { }
                        val req = when {
                            target == 0 -> state.first
                            total > 1 && target == total - 1 -> state.last
                            else -> state.jump
                        }
                        try {
                            req.requestFocus()
                        } catch (_: IllegalStateException) {
                        }
                        state.clearJump()
                    }
                    true
                }
            }
        }
}
