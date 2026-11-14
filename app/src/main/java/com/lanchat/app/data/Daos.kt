package com.lanchat.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun messagesForPeer(peerId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestOverall(): MessageEntity?

    /** 聊天列表：每个 peer 的最后一条消息 */
    @Query("SELECT * FROM messages WHERE id IN (SELECT MAX(id) FROM messages GROUP BY peerId) ORDER BY timestamp DESC")
    fun latestPerPeer(): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE peerId = :peerId AND isMe = 0 AND status = 'received'")
    fun unreadCount(peerId: String): Flow<Int>

    @Query("UPDATE messages SET status = 'read' WHERE peerId = :peerId AND isMe = 0")
    suspend fun markRead(peerId: String)

    @Query("UPDATE messages SET progress = :progress, status = :status WHERE fileId = :fileId")
    suspend fun updateFileProgress(fileId: String, progress: Int, status: String)

    @Query("UPDATE messages SET filePath = :path, status = 'received', progress = 100 WHERE fileId = :fileId")
    suspend fun markFileReceived(fileId: String, path: String): Int

    @Query("UPDATE messages SET filePath = :path, status = 'received', progress = 100 WHERE peerId = :peerId AND fileName = :fileName AND fileSize = :size AND status = 'receiving'")
    suspend fun markFileReceivedByName(peerId: String, fileName: String, size: Long, path: String): Int

    @Query("DELETE FROM messages WHERE peerId = :peerId")
    suspend fun deleteForPeer(peerId: String)
}

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contact: ContactEntity)

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun allContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ContactEntity?

    @Query("UPDATE contacts SET isOnline = :online, lastSeen = :now WHERE id = :id")
    suspend fun setOnline(id: String, online: Boolean, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(moment: MomentEntity): Long

    @Update
    suspend fun update(moment: MomentEntity)

    @Query("SELECT * FROM moments ORDER BY timestamp DESC")
    fun allMoments(): Flow<List<MomentEntity>>

    @Query("SELECT * FROM moments WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MomentEntity?

    @Query("DELETE FROM moments WHERE id = :id")
    suspend fun delete(id: Long)
}
