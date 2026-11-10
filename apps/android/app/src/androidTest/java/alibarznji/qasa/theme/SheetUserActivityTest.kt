package alibarznji.qasa.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A bottom sheet is its own window, so `MainActivity.onUserInteraction` never sees what
 * happens in it. Touches and typing inside [QasaModalBottomSheet] must still reach the
 * auto-lock through [LocalUserActivity], or the vault locks while an item is being edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class SheetUserActivityTest {
    @get:Rule val compose = createComposeRule()

    private val count = AtomicInteger()

    private fun showSheet() {
        compose.setContent {
            CompositionLocalProvider(LocalUserActivity provides UserActivitySink { count.incrementAndGet() }) {
                QasaTheme {
                    QasaModalBottomSheet(onDismissRequest = {}) {
                        var text by remember { mutableStateOf("") }
                        Text("Tap target")
                        ThemedTextField(value = text, onValueChange = { text = it }, placeholder = "Title")
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun nothingCountsUntilTheUserActs() {
        showSheet()
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(0, count.get())
    }

    @Test
    fun aTapInsideTheSheetCounts() {
        showSheet()
        compose.onNodeWithText("Tap target").performClick()
        compose.waitForIdle()
        assertTrue("tap was not reported", count.get() > 0)
    }

    @Test
    fun typingInTheSheetCounts() {
        showSheet()
        // performTextInput goes through the text input session like the soft keyboard,
        // with no touch on the sheet.
        compose.onNode(hasSetTextAction()).performTextInput("github.com")
        compose.waitForIdle()
        assertTrue("typing was not reported", count.get() > 0)
    }
}
