package com.lanchat.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanchat.app.ui.theme.AvatarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Avatar(name: String, colorIndex: Int, sizeDp: Int = 44) {
    val color = AvatarColors[colorIndex.mod(AvatarColors.size)]
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(RoundedCornerShape(6.dp)).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name.take(1).ifBlank { "?" }, color = Color.White, fontSize = (sizeDp * 0.45).sp)
    }
}

@Composable
fun AvatarCircle(name: String, colorIndex: Int, sizeDp: Int = 44) {
    val color = AvatarColors[colorIndex.mod(AvatarColors.size)]
    Box(
        modifier = Modifier.size(sizeDp.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text = name.take(1).ifBlank { "?" }, color = Color.White, fontSize = (sizeDp * 0.45).sp)
    }
}

fun formatTime(ts: Long): String {
    val now = System.currentTimeMillis()
    val fmt = when {
        now - ts < 60_000 -> return "刚刚"
        now - ts < 3_600_000 -> SimpleDateFormat("HH:mm", Locale.getDefault())
        isSameDay(ts, now) -> SimpleDateFormat("HH:mm", Locale.getDefault())
        isSameDay(ts, now - 86_400_000) -> return "昨天"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault())
    }
    return fmt.format(Date(ts))
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val f = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return f.format(Date(a)) == f.format(Date(b))
}

fun formatDateTime(ts: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
