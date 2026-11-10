package alibarznji.qasa.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Receives "the user did something" for the inactivity auto-lock.
 *
 * `MainActivity.onUserInteraction` only sees its own window. Sheets and dialogs are
 * separate windows, and soft-keyboard typing arrives through the IME connection rather
 * than as key events, so neither ever reaches it: a user filling in the New Login sheet
 * for five minutes was locked out mid-edit. Those surfaces report here instead.
 */
fun interface UserActivitySink {
    fun noteActivity()
}

/** Provided by `MainActivity` as `AutoLockMonitor::noteActivity`; a no-op elsewhere. */
val LocalUserActivity = staticCompositionLocalOf { UserActivitySink {} }

/**
 * Whether a pointer event is the user's own doing. Presses, releases and scrolls are; a
 * move only while something is pressed, because Compose synthesizes hover moves when the
 * layout shifts under a resting mouse. Enter/exit are hover bookkeeping, not input.
 */
internal fun countsAsUserActivity(type: PointerEventType, anyPressed: Boolean): Boolean =
    when (type) {
        PointerEventType.Press, PointerEventType.Release, PointerEventType.Scroll -> true
        PointerEventType.Move -> anyPressed
        else -> false
    }

private fun PointerEvent.countsAsUserActivity(): Boolean =
    countsAsUserActivity(type, changes.any { it.pressed || it.previousPressed })

/**
 * Reports touches and key presses anywhere inside this node to [sink] without consuming
 * them: the pointer pass is `Initial` and the key handler returns `false`, so children
 * still get every event.
 */
fun Modifier.reportUserActivity(sink: UserActivitySink): Modifier =
    pointerInput(sink) {
        awaitPointerEventScope {
            while (true) {
                if (awaitPointerEvent(PointerEventPass.Initial).countsAsUserActivity()) {
                    sink.noteActivity()
                }
            }
        }
    }.onPreviewKeyEvent {
        sink.noteActivity()
        false
    }
