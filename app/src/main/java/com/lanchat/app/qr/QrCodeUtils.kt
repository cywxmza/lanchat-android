package com.lanchat.app.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** 二维码内容：JSON，扫码方解析后直接得到 IP/端口/昵称/设备 id。 */
data class QrPayload(val ip: String, val port: Int, val name: String, val id: String)

object QrCodeUtils {
    private val gson = Gson()

    fun encodePayload(ip: String, port: Int, name: String, id: String): String =
        gson.toJson(QrPayload(ip, port, name, id))

    /** 解析二维码文本；不是本 App 的二维码时返回 null（调用方提示“不是局域传信的码”）。 */
    fun parsePayload(text: String): QrPayload? = try {
        gson.fromJson(text.trim(), QrPayload::class.java)
            .takeIf { it.ip.isNotBlank() && it.port in 1..65535 && it.id.isNotBlank() }
    } catch (_: Exception) { null }

    /** 用 ZXing 生成黑白二维码 Bitmap（size 像素边长）。 */
    fun generateBitmap(content: String, size: Int = 512): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) for (y in 0 until size) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return bitmap
    }
}
