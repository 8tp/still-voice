package dev.chuds.stillvoice

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chuds.stillvoice.recorder.RecordingService
import dev.chuds.stillvoice.ui.theme.StillTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val openedFromNotification = consumeNotificationOpenIfAny()

        setContent {
            StillTheme {
                StillVoiceApp(initialOpenRecordingState = openedFromNotification)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Honor a tap on the foreground notification: nothing to do beyond
        // bringing the activity forward; the bar reads RecorderController.state
        // so it's already in the right state.
        setIntent(intent)
    }

    private fun consumeNotificationOpenIfAny(): Boolean {
        val current = intent ?: return false
        if (current.action != RecordingService.ACTION_OPEN_FROM_NOTIFICATION) return false
        current.action = null
        return true
    }
}
