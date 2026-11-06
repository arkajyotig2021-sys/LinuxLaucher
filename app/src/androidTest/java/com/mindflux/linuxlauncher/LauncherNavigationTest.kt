package com.mindflux.linuxlauncher

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import androidx.test.rule.GrantPermissionRule

class LauncherNavigationTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionAndComposeRule: TestRule = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(composeRule)

    @Test
    fun allFourNavigationSectionsOpenTheirScreens() {
        composeRule.onNodeWithText("A new kind of\nLinux workspace.").assertIsDisplayed()

        composeRule.onNodeWithText("Explore").performClick()
        composeRule.onNodeWithText("Find your distro.").assertIsDisplayed()
        composeRule.onNodeWithText("Ubuntu").assertIsDisplayed()

        composeRule.onNodeWithText("My device").performClick()
        composeRule.onNodeWithText("Your device.").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Settings.").assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("A new kind of\nLinux workspace.").assertIsDisplayed()
    }
}
