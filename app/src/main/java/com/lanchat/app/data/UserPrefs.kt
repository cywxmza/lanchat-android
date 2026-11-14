package com.lanchat.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "lanchat_prefs")

/** 本机身份：昵称、头像色、设备 id、服务器端口。存在 DataStore，重启不变。 */
class UserPrefs(private val context: Context) {
    private val KEY_NAME = stringPreferencesKey("my_name")
    private val KEY_COLOR = intPreferencesKey("avatar_color")
    private val KEY_ID = stringPreferencesKey("device_id")
    private val KEY_PORT = intPreferencesKey("server_port")

    val myName: Flow<String> = context.dataStore.data.map { it[KEY_NAME] ?: "我" }
    val avatarColor: Flow<Int> = context.dataStore.data.map { it[KEY_COLOR] ?: 0 }
    val serverPort: Flow<Int> = context.dataStore.data.map { it[KEY_PORT] ?: 8080 }

    suspend fun myId(): String {
        val current = context.dataStore.data.first()[KEY_ID]
        if (current != null) return current
        val id = "dev-" + UUID.randomUUID().toString().take(8)
        context.dataStore.edit { it[KEY_ID] = id }
        return id
    }

    suspend fun setName(name: String) { context.dataStore.edit { it[KEY_NAME] = name } }
    suspend fun setAvatarColor(color: Int) { context.dataStore.edit { it[KEY_COLOR] = color } }
    suspend fun setPort(port: Int) { context.dataStore.edit { it[KEY_PORT] = port } }
}
