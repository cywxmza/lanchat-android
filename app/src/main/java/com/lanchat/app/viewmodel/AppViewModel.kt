package com.lanchat.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lanchat.app.data.AppDatabase
import com.lanchat.app.data.ContactEntity
import com.lanchat.app.data.MomentEntity
import com.lanchat.app.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val prefs = UserPrefs(app)

    val lanManager = LanManager(app, viewModelScope)

    val chatList = db.messageDao().latestPerPeer()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val contacts = db.contactDao().allContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val moments = db.momentDao().allMoments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val myName = prefs.myName.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "我")
    val myColor = prefs.avatarColor.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val serverPort = prefs.serverPort.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8080)

    init { viewModelScope.launch { lanManager.init() } }

    fun messagesFor(peerId: String) = db.messageDao().messagesForPeer(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unreadCount(peerId: String) = db.messageDao().unreadCount(peerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun markRead(peerId: String) = viewModelScope.launch(Dispatchers.IO) { db.messageDao().markRead(peerId) }

    fun setMyName(name: String) = viewModelScope.launch { prefs.setName(name) }
    fun setMyColor(color: Int) = viewModelScope.launch { prefs.setAvatarColor(color) }
    fun setPort(port: Int) = viewModelScope.launch {
        prefs.setPort(port)
        // 端口改动需重启 App 生效（LanServer 已绑定旧端口），此处只存设置并提示。
    }

    fun addContact(contact: ContactEntity) = viewModelScope.launch(Dispatchers.IO) { db.contactDao().upsert(contact) }

    fun postMoment(text: String, imagePaths: List<String>) = viewModelScope.launch(Dispatchers.IO) {
        db.momentDao().insert(MomentEntity(authorId = lanManager.myId, authorName = myName.value, authorColor = myColor.value, text = text, imagePaths = imagePaths.joinToString("|")))
    }

    fun toggleLike(moment: MomentEntity) = viewModelScope.launch(Dispatchers.IO) {
        val liked = !moment.likedByMe
        db.momentDao().update(moment.copy(likedByMe = liked, likeCount = (moment.likeCount + if (liked) 1 else -1).coerceAtLeast(0)))
    }

    fun addComment(moment: MomentEntity, commentText: String) = viewModelScope.launch(Dispatchers.IO) {
        val arr = try { JSONArray(moment.commentsJson) } catch (_: Exception) { JSONArray() }
        arr.put(JSONObject().apply {
            put("author", myName.value); put("text", commentText); put("time", System.currentTimeMillis())
        })
        db.momentDao().update(moment.copy(commentsJson = arr.toString()))
    }

    fun deleteMoment(moment: MomentEntity) = viewModelScope.launch(Dispatchers.IO) { db.momentDao().delete(moment.id) }

    override fun onCleared() { lanManager.stop(); super.onCleared() }
}
