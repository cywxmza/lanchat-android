package com.lanchat.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lanchat.app.data.MomentEntity
import com.lanchat.app.viewmodel.AppViewModel
import org.json.JSONArray
import java.io.File

@Composable
fun DiscoverScreen(vm: AppViewModel) {
    val moments by vm.moments.collectAsState()
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var pickedImages by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pickedImages = uris
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("发一条朋友圈（仅保存在本机）", fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("这一刻的想法…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { pickImages.launch("image/*") }) { Text("选图片 (${pickedImages.size})") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (draft.isBlank() && pickedImages.isEmpty()) return@Button
                        val paths = pickedImages.mapNotNull { uri ->
                            val f = File(context.cacheDir, "moment_${System.currentTimeMillis()}_${uri.lastPathSegment?.takeLast(20) ?: "img"}.jpg")
                            try { context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { out -> input.copyTo(out) } }; f.absolutePath } catch (_: Exception) { null }
                        }
                        vm.postMoment(draft.trim(), paths)
                        draft = ""; pickedImages = emptyList()
                    }) { Text("发表") }
                }
            }
            Divider()
        }
        items(moments, key = { it.id }) { m -> MomentCard(m, vm) }
        if (moments.isEmpty()) {
            item { Text("还没有动态，发一条试试。", modifier = Modifier.padding(16.dp), fontSize = 14.sp, color = Color.Gray) }
        }
    }
}

@Composable
private fun MomentCard(m: MomentEntity, vm: AppViewModel) {
    var commentDraft by remember { mutableStateOf("") }
    var showComment by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row {
            Avatar(m.authorName, m.authorColor, 40)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(m.authorName, fontSize = 15.sp, color = Color(0xFF576B95))
                Text(formatDateTime(m.timestamp), fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(Modifier.height(8.dp))
        if (m.text.isNotBlank()) Text(m.text, fontSize = 15.sp)
        val images = m.imagePaths.split("|").filter { it.isNotBlank() }
        images.forEach { p ->
            Spacer(Modifier.height(8.dp))
            if (File(p).exists()) AsyncImage(model = File(p), contentDescription = null, modifier = Modifier.size(180.dp), contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            IconButton(onClick = { vm.toggleLike(m) }) {
                Icon(if (m.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = "点赞", tint = if (m.likedByMe) Color.Red else Color.Gray)
            }
            Text("${m.likeCount}", modifier = Modifier.padding(top = 14.dp), fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.width(16.dp))
            Text("评论", modifier = Modifier.padding(top = 12.dp).clickable { showComment = !showComment }, fontSize = 14.sp, color = Color(0xFF576B95))
            Spacer(Modifier.width(16.dp))
            if (m.authorId == vm.lanManager.myId) {
                Text("删除", modifier = Modifier.padding(top = 12.dp).clickable { vm.deleteMoment(m) }, fontSize = 14.sp, color = Color.Red)
            }
        }
        val comments = try {
            val arr = JSONArray(m.commentsJson)
            (0 until arr.length()).map { arr.getJSONObject(it).getString("author") + "：" + arr.getJSONObject(it).getString("text") }
        } catch (_: Exception) { emptyList() }
        comments.forEach { c -> Text(c, fontSize = 14.sp, color = Color.DarkGray, modifier = Modifier.padding(vertical = 2.dp)) }
        if (showComment) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = commentDraft, onValueChange = { commentDraft = it }, label = { Text("写评论…") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (commentDraft.isNotBlank()) { vm.addComment(m, commentDraft.trim()); commentDraft = ""; showComment = false } }) {
                    Icon(Icons.Default.Send, contentDescription = "发送评论", tint = Color(0xFF07C160))
                }
            }
        }
    }
    Divider()
}
