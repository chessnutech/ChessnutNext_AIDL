# 第三方 App 接入 USB 棋钟按钮（AIDL 方案）

本文档面向**第三方开发者**，介绍如何在自己的 Android app 中接入 Chessnut 棋钟按钮，读取按钮事件并发送控制命令。

## 背景

USB 设备一次只能被一个进程独占。Chessnut app 作为 **broker（代理）**，独占持有 USB 连接，并通过 AIDL 跨进程接口把读写能力开放给其他 app。任意 app 都可绑定，无需授权或签名校验。

```
┌─────────────────────────────┐
│  Chessnut App（Broker，独占 USB）       │
│   UsbClockService（导出绑定服务）        │
└───────┬──────────────┬───────┘
        │ bindService     │ bindService
   ┌──▼─────┐    ┌──▼─────┐
   │ 你的 App A │    │ 你的 App B │
   └──────────┘    └──────────┘
```

- 多个 app 可**同时**绑定，共享同一个设备。
- 按钮事件会**广播**给所有已注册的 app。
- 控制命令（如切换激活侧）任意 app 都可发送。

## 前置条件

- 设备上已安装 Chessnut app（应用包名 `com.chessnut.newchessnut`）。
- Chessnut app 已获得 USB 权限并连接到棋钟设备。
- 你的 app `minSdk >= 24`。

## 接入步骤

### 1. 复制 AIDL 文件

把以下两个文件**原样**复制到你的工程，保持包路径 `com.chessnut.chessnutnext.clock` 不变：

```
src/main/aidl/com/chessnut/chessnutnext/clock/IUsbClockService.aidl
src/main/aidl/com/chessnut/chessnutnext/clock/IUsbClockListener.aidl
```

> 包路径必须与 broker 端完全一致，否则 AIDL 序列化的接口描述符对不上，绑定会失败。

### 2. 开启 AIDL 构建特性

在你的 app `build.gradle.kts`：

```kotlin
android {
    buildFeatures {
        aidl = true
    }
}
```

（Groovy DSL：`android { buildFeatures { aidl true } }`）

### 3. 声明 package 可见性（Android 11+）

Android 11（API 30）起有包可见性限制。在你的 `AndroidManifest.xml` 顶层加：

```xml
<queries>
    <package android:name="com.chessnut.newchessnut" />
</queries>
```

否则在 Android 11+ 上 `bindService` 可能静默失败。

### 4. 绑定服务

使用**显式 Intent**（指定包名 + action）绑定：

```kotlin
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.chessnut.chessnutnext.clock.IUsbClockService
import com.chessnut.chessnutnext.clock.IUsbClockListener

class UsbClockClient(private val context: Context) {

    companion object {
        private const val BROKER_PACKAGE = "com.chessnut.newchessnut"
        private const val BIND_ACTION = "com.chessnut.chessnutnext.clock.UsbClockService"
    }

    private var service: IUsbClockService? = null

    private val listener = object : IUsbClockListener.Stub() {
        override fun onConnectionStateChanged(state: Int) {
            // 0 = DISCONNECTED, 1 = CONNECTING, 2 = CONNECTED
            // 注意：回调在 Binder 线程，更新 UI 需切回主线程
        }

        override fun onButtonEvent(side: Int, pressed: Boolean, timestamp: Long) {
            // side: 0 = LEFT, 1 = RIGHT
        }

        override fun onError(error: String?) {
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = IUsbClockService.Stub.asInterface(binder).also {
                it.registerListener(listener)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun bind(): Boolean {
        val intent = Intent(BIND_ACTION).apply {
            setPackage(BROKER_PACKAGE)
        }
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        try {
            service?.unregisterListener(listener)
        } catch (_: Exception) {}
        context.unbindService(connection)
        service = null
    }

    // ---- 控制命令 ----

    /** 获取连接状态：0=未连接, 1=连接中, 2=已连接 */
    fun getConnectionState(): Int = service?.connectionState ?: 0

    /** 主动发起连接 */
    fun connect(): Boolean = service?.connect() ?: false

    /** 切换激活侧：0=LEFT, 1=RIGHT */
    fun setActiveSide(side: Int): Boolean = service?.setActiveSide(side) ?: false
}
```

### 5. 生命周期

```kotlin
val client = UsbClockClient(context)

// Activity.onStart / 需要时
client.bind()

// Activity.onStop / 不再需要时
client.unbind()
```

## 接口参考

### IUsbClockService（控制接口）

| 方法 | 说明 | 返回 |
|------|------|------|
| `registerListener(listener)` | 注册事件回调 | void |
| `unregisterListener(listener)` | 注销事件回调 | void |
| `getConnectionState()` | 当前连接状态 | `0`=DISCONNECTED, `1`=CONNECTING, `2`=CONNECTED |
| `connect()` | 主动发起连接 | `boolean` 是否已连接/已发起 |
| `setActiveSide(side)` | 切换激活侧（控制 LED/指示） | `boolean` 命令是否发送成功 |

### IUsbClockListener（事件回调）

| 方法 | 参数 | 说明 |
|------|------|------|
| `onConnectionStateChanged(state)` | `state`: 0/1/2 | 连接状态变化。注册时会立即回报一次当前状态 |
| `onButtonEvent(side, pressed, timestamp)` | `side`: 0=LEFT 1=RIGHT；`pressed`: 是否按下；`timestamp`: 毫秒 | 按钮事件 |
| `onError(error)` | `error`: 错误描述 | 错误回调 |

### 常量对照

| 含义 | 值 |
|------|----|
| 按钮 LEFT | `0` |
| 按钮 RIGHT | `1` |
| 状态 DISCONNECTED | `0` |
| 状态 CONNECTING | `1` |
| 状态 CONNECTED | `2` |

## 注意事项

- **回调线程**：`IUsbClockListener` 的回调运行在 Binder 线程池，不是主线程。更新 UI 请用 `Handler(Looper.getMainLooper())` 或 `runOnUiThread` 切回主线程。
- **事件去重**：broker 按设备上报频率原样广播按钮事件（设备会持续上报当前状态），如只关心"状态变化"需自行做去重。
- **broker 未运行**：若 Chessnut app 从未启动，`BIND_AUTO_CREATE` 会拉起它的 service。但 USB 权限弹窗只能由 Chessnut app 处理，首次使用建议先打开一次 Chessnut app 授权。
- **多 app 共存**：你注销或进程退出后，`RemoteCallbackList` 会自动清理你的回调，不影响其他 app。
- **断线重连**：broker 内部已实现 USB 断开后自动重连（含休眠恢复），你只需监听 `onConnectionStateChanged` 即可感知状态。

## 故障排查

| 现象 | 可能原因 |
|------|----------|
| `bindService` 返回 false | 未加 `<queries>`（Android 11+）；或 action/包名拼错 |
| 绑定成功但收不到事件 | 忘记 `registerListener`；或设备未连接（先查 `getConnectionState`） |
| `setActiveSide` 返回 false | 设备未连接，或 side 取值非 0/1 |
| 类找不到 / 转型失败 | AIDL 文件包路径与 broker 不一致 |
