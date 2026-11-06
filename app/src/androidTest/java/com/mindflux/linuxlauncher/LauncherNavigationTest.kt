package com.mindflux.linuxlauncher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class LauncherNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

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
