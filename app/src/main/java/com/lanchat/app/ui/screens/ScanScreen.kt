package com.lanchat.app.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.lanchat.app.data.ContactEntity
import com.lanchat.app.qr.QrCodeUtils
import com.lanchat.app.viewmodel.AppViewModel
import java.util.concurrent.Executors

/**
 * 扫一扫：CameraX 预览 + ML Kit 条码扫描，扫到本 App 二维码后解析
 * QrPayload，自动加为好友并 Socket 直连；不是本 App 的码会提示。
 */
@Composable
fun ScanScreen(vm: AppViewModel, onConnected: (peerId: String, peerName: String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("将二维码放入框内，即可自动扫描") }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            val scanner = BarcodeScanning.getClient(
                                BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                            )
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                if (scanned) { imageProxy.close(); return@setAnalyzer }
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(image)
                                        .addOnSuccessListener { barcodes ->
                                            val raw = barcodes.firstOrNull()?.rawValue ?: return@addOnSuccessListener
                                            val payload = QrCodeUtils.parsePayload(raw)
                                            if (payload == null) {
                                                status = "不是局域传信的二维码，请扫对方 App 里的「我的二维码」"
                                                return@addOnSuccessListener
                                            }
                                            if (scanned) return@addOnSuccessListener
                                            scanned = true
                                            status = "已扫到 ${payload.name}（${payload.ip}:${payload.port}），正在连接…"
                                            vm.addContact(ContactEntity(payload.id, payload.name, payload.ip, payload.port, 0))
                                            vm.lanManager.connect(payload.ip, payload.port) { ok, msg ->
                                                if (ok) {
                                                    Toast.makeText(context, "已连接 ${payload.name}", Toast.LENGTH_SHORT).show()
                                                    onConnected(payload.id, payload.name)
                                                } else {
                                                    scanned = false
                                                    status = "连接失败：$msg，请确认在同一 Wi-Fi 后重扫"
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        }
                                        .addOnFailureListener { e -> Log.w("ScanScreen", "scan failed", e) }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else imageProxy.close()
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        } catch (e: Exception) {
                            Log.e("ScanScreen", "camera bind failed", e)
                            status = "相机启动失败：${e.message}"
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
            Text("扫一扫", modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp), color = Color.White, fontSize = 18.sp)
        }
        Text(status, modifier = Modifier.fillMaxWidth().padding(16.dp), fontSize = 14.sp, color = Color.DarkGray)
    }
}
