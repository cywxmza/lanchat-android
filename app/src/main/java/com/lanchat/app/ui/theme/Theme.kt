package com.lanchat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LanChatColors = lightColorScheme(
    primary = WeChatGreen,
    secondary = WeChatGreenDark,
    background = ChatBg,
    surface = BubbleOther,
    onPrimary = androidx.compose.ui.graphics.Color.White
)

@Composable
fun LanChatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LanChatColors, typography = Typography, content = content)
}
