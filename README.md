# 局域传信 LanChat — 原生 Android 应用

微信风格的局域网聊天 + 文件互传 App，**原生 Kotlin + Jetpack Compose** 实现。
两台手机连同一 Wi-Fi，扫码直连，不经过任何服务器，消息和文件只在局域网内走 TCP Socket。

> 与微信无关：未使用微信商标、Logo、官方素材，仅借鉴其布局与交互（微信绿 `#07C160`、底部 4 Tab、聊天气泡）。App 名：**局域传信**（LanChat）。

## 功能清单

| 模块 | 功能 |
|---|---|
| 微信（聊天列表） | 会话列表、最后一条消息、时间、未读数、在线绿点 |
| 聊天 | 文字、系统表情选择器、图片（相册）、文件（SAF 任选）、语音（按住说话，MediaRecorder 录音，点按 MediaPlayer 播放）、文件消息卡片（文件名/大小/进度条/点开） |
| 通讯录 | 已连接/已添加好友、在线状态、点开进聊天、扫码加好友、手动输 IP 连接、搜索同一 Wi-Fi 设备 |
| 发现（朋友圈） | 发动态（文字+图片）、点赞、评论、删除自己的动态，存 Room |
| 我 | 昵称/头像色设置、我的二维码、扫一扫入口、服务器端口设置、接收路径说明、关于页 |
| 局域网连接 | 本机 TCP 服务器（默认 8080，可改），WifiManager 取本机 IP，ZXing 生成二维码；对方 CameraX + ML Kit 扫码解析 IP/端口后 Socket 直连；握手交换昵称/头像色 |
| 文件传输 | 先发 `file-meta` JSON 头，再 16 KiB 分片发二进制，接收方按 fileId 组装写到 `Download/LanChat/`，完成后回 `file-done` 回执，聊天界面显示进度 |
| 保活 | 前台 Service（`LanServerService`，带通知），后台也能接收文件；断线 Toast 提示，可重新扫码连接 |
| 存储 | Room 存消息/联系人/朋友圈；DataStore 存昵称/头像色/设备 ID/端口 |
| 权限 | CAMERA、RECORD_AUDIO、POST_NOTIFICATIONS、NEARBY_WIFI_DEVICES、INTERNET、ACCESS_WIFI_STATE 等运行时申请；文件用 SAF + MediaStore 思路，不要求 root |

## 两台手机使用步骤

1. 两台手机连**同一个 Wi-Fi**（同一路由器，同一网段，如都是 `192.168.1.x`）。
2. 两台都安装本 App 并打开，首次打开时允许相机、麦克风、通知权限。
3. 手机 A：点底部「我」→「我的二维码」（或右上角 `+` → 我的二维码）。
4. 手机 B：点右上角扫一扫图标（或「通讯录」→「扫一扫加好友」），扫 A 的码。
5. 扫到后自动连接，通讯录里出现对方，在线绿点亮起，点开即可发消息。
6. 传文件：在聊天界面点 `+`（回形针）→「文件」选任意文件，或「图片」选照片；对方收到后在文件卡片点「打开」，文件本体在 `手机存储/Download/LanChat/`。
7. 连不上时：先确认在同一 Wi-Fi，再在「通讯录」手动输对方 IP（如 `192.168.1.5`）点连接；或在「我」页看本机 IP 是否为空。

## 在 Android Studio 打开并出 APK

```bash
# 1. 用 Android Studio（Hedgehog 及以上）打开本目录 lanchat-android/
#    首次打开 IDE 会自动下载 Gradle 8.7、Android Gradle Plugin 8.5.2、
#    compileSdk 34 所需的 SDK，并生成 gradle-wrapper.jar。
# 2. 同步 Gradle（Sync Now），等依赖下载完成。
# 3. 连一台 Android 8.0+ 真机（或启动模拟器），点 Run ▶ 即可安装调试。
# 4. 出安装包（命令行，在工程根目录）：
./gradlew assembleDebug
# APK 位置：app/build/outputs/apk/debug/app-debug.apk
# 发正式包：
./gradlew assembleRelease
```

> 注意：本工程附带的 `gradlew` 是精简启动脚本——若本地已有系统 Gradle 会直接调用它；若 `gradle/wrapper/gradle-wrapper.jar` 不存在且无系统 Gradle，请在 Android Studio 里打开工程一次，IDE 会补全 wrapper，之后 `./gradlew` 即可正常用。**本机环境没有 Android SDK，因此本次交付未编译出 APK**，以上步骤需在装有 Android Studio / SDK 的电脑上执行。

## 电脑卡跑不动？用 GitHub 云端编译（不用装 Android Studio）

工程里已带 `.github/workflows/build-apk.yml`，GitHub 的云端机器会帮你编译：

1. 在 GitHub 新建一个空仓库（私有也行），把本工程整个推上去（手机浏览器也能在 github.com 上直接上传 zip 后解压不了——建议在电脑浏览器上操作，或用 GitHub 手机 App 上传文件）。
2. 推上去后点仓库顶部的 **Actions**，会看到 **Build APK** 正在跑，几分钟跑完。
3. 跑完点进去，在最下面的 **Artifacts** 里下载 **app-debug**，解压就是 `app-debug.apk`，传到手机安装就行。

全程不用你电脑跑 Android Studio，只需要能上传代码、能下载 APK，卡电脑也能做。注意：这是 GitHub 免费的公开 Runner，编译时如果代码有报错，Actions 日志里会显示哪一行，把报错发回来我帮你修。

## 技术栈

- 语言：Kotlin 2.0.20（原生，非跨平台框架）
- UI：Jetpack Compose（BOM 2024.09.00）+ Material 3 + Navigation Compose 2.7.7
- 存储：Room 2.6.1（消息/联系人/朋友圈）+ DataStore Preferences（本机身份）
- 扫码：CameraX 1.3.4 + ML Kit Barcode Scanning 17.3.0；二维码生成：ZXing Core 3.5.3
- 图片：Coil 2.7.0；协议 JSON：Gson 2.11.0
- minSdk 26（Android 8.0）/ targetSdk 34 / compileSdk 34 / Java 17

## 协议说明（局域传信协议 v1，见 `net/Protocol.kt`）

传输层：TCP 长连接，一条 Socket 对应一个对端，服务端、客户端共用同一 `PeerConnection` 读/写逻辑。

**控制帧**：一行 JSON + `\n`（UTF-8）：

```json
{"type":"handshake","id":"dev-xxxxxxxx","name":"小明","avatarColor":2,"port":8080}
{"type":"chat","id":"dev-xxxxxxxx","name":"小明","text":"你好","time":1730000000000}
{"type":"heartbeat","time":1730000000000}
{"type":"file-meta","fileId":"<uuid>","fileName":"报告.pdf","fileSize":123456,"mime":"application/pdf"}
{"type":"file-done","fileId":"<uuid>","ok":true}
```

**文件体帧**：紧跟在 `file-meta` 行之后，原始二进制，按 16 KiB 一片顺序发送；接收方累计读满 `fileSize` 字节即完成（不靠换行符分隔，读 JSON 行时按字节读到 `\n`，避免 `BufferedReader` 预读吞掉后续二进制）。完成后接收方回 `file-done`，发送方把本地消息状态置为 `sent`，失败置 `failed`。

**二维码内容**：JSON，如 `{"ip":"192.168.1.5","port":8080,"name":"小明","id":"dev-xxxxxxxx"}`。

**心跳/断线**：每 15 秒发一次 `heartbeat`，45 秒无任何帧则认为断线，关闭连接并通知 UI。

## 注意事项

- 必须同一 Wi-Fi / 同一网段；手机开热点给另一台连也可以（热点手机的 IP 通常是 `192.168.43.x`）。
- 部分路由器开启了「AP 隔离」会阻止手机互访，需在路由器里关闭 AP 隔离。
- 语音、图片、文件都按文件消息走同一通道发送；大文件建议在 Wi-Fi 信号好的地方传，进度条会实时更新。
- 接收目录 `Download/LanChat/` 是公开下载目录，卸载 App 后文件仍保留。
- 端口改动后需重启 App 生效（`LanServer` 启动时绑定端口）。

## 目录结构

```
lanchat-android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml        # 全部依赖版本
├── gradlew / gradle/wrapper/
├── README.md / .gitignore
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml     # 权限 + MainActivity + LanServerService + FileProvider
        ├── res/values/themes.xml, strings.xml / res/xml/file_paths.xml
        └── java/com/lanchat/app/
            ├── MainActivity.kt     # 导航（4 Tab + 聊天/二维码/扫码）+ 权限申请 + 启动前台服务
            ├── ui/theme/           # Color / Theme / Type（微信绿 #07C160）
            ├── ui/screens/         # ChatListScreen / ChatScreen / ContactsScreen /
            │                       # DiscoverScreen / ProfileScreen / QrScreen / ScanScreen / Common
            ├── data/               # AppDatabase / Daos / Entities / UserPrefs
            ├── net/                # Protocol / WifiUtils / FileTransfer /
            │                       # PeerConnection / LanServer / LanClient
            ├── qr/QrCodeUtils.kt   # ZXing 生成二维码 + 解析
            ├── service/LanServerService.kt
            └── viewmodel/          # LanManager（连接/收发/落库）/ AppViewModel
```
