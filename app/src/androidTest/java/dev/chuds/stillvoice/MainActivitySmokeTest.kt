package dev.chuds.stillvoice

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val permissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.POST_NOTIFICATIONS,
    )

    // Permissions must be granted before the activity launches so the cold-start
    // path under test sees micGranted=true without triggering a runtime prompt.
    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun coldStart_showsRecordingsHomeAndRecordButton() {
        composeRule.onNodeWithText("RECORDINGS").assertIsDisplayed()
        composeRule.onNodeWithText("record").assertIsDisplayed()
    }
}
