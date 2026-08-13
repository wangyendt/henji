package com.qingheng.weight

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShortcutToImportCanReturnWithBottomHome() {
        composeRule.onNodeWithText("导入数据").performClick()
        composeRule.onNodeWithText("导入身体数据").assertIsDisplayed()

        composeRule.onNodeWithText("首页").performClick()

        composeRule.onNodeWithText("衡迹").assertIsDisplayed()
    }
}
