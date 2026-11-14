package com.lanchat.app.net

import android.content.Context
import android.os.Environment
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 文件传输：发送方先写 file-meta JSON 行，再写原始二进制（16 KiB/片）；
 * 接收方读完 file-meta 后按 size 精确读满字节，写到 Download/LanChat/。
 * 进度回调在 IO 线程触发，调用方自行切到主线程更新 UI / Room。
 */
object FileTransfer {
    fun receiveDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "LanChat")
            .apply { if (!exists()) mkdirs() }

    /** 发送文件体。meta 已由调用方先行发出，本函数只写二进制并回调进度。 */
    fun sendFileBody(
        output: BufferedOutputStream,
        input: InputStream,
        totalSize: Long,
        onProgress: (sent: Long, total: Long) -> Unit
    ) {
        val buf = ByteArray(Protocol.CHUNK_SIZE)
        var sent = 0L
        var n: Int
        val bis = BufferedInputStream(input)
        while (bis.read(buf).also { n = it } != -1) {
            synchronized(output) {
                output.write(buf, 0, n)
                output.flush()
            }
            sent += n
            onProgress(sent, totalSize)
        }
    }

    /** 接收文件体：从 input 精确读满 totalSize 字节，写入目标文件。 */
    fun receiveFileBody(
        input: BufferedInputStream,
        totalSize: Long,
        target: File,
        onProgress: (received: Long, total: Long) -> Unit
    ): File {
        val buf = ByteArray(Protocol.CHUNK_SIZE)
        var received = 0L
        FileOutputStream(target).use { fos ->
            while (received < totalSize) {
                val want = minOf(Protocol.CHUNK_SIZE.toLong(), totalSize - received).toInt()
                var read = 0
                while (read < want) {
                    val n = input.read(buf, read, want - read)
                    if (n == -1) throw java.io.EOFException("文件传输中断：已收 $received / $totalSize")
                    read += n
                }
                fos.write(buf, 0, read)
                received += read
                onProgress(received, totalSize)
            }
            fos.flush()
        }
        return target
    }

    /** 用 SAF 的 Uri 打开输入流时，调用方传 contentResolver.openInputStream(uri)。 */
    fun copyStreamToFile(input: InputStream, target: File) {
        FileOutputStream(target).use { out -> input.copyTo(out) }
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var i = 1
        while (f.exists()) {
            f = File(dir, if (ext.isEmpty()) "$base($i)" else "$base($i).$ext")
            i++
        }
        return f
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    }
}
