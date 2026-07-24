# Third-Party App Integration: USB Chess Clock Buttons (AIDL)

This document is for **third-party developers** who want to integrate the Chessnut chess clock buttons into their own Android app — reading button events and sending control commands.

> The current AIDL interface no longer exposes a `pressed` parameter. Existing
> clients must copy both updated `.aidl` files and rebuild because the Binder
> interface signature is not backward-compatible.

## Background

A USB device can only be claimed exclusively by one process at a time. The Chessnut app acts as a **broker**: it holds the USB connection exclusively and exposes read/write capabilities to other apps through an AIDL cross-process interface. Any app may bind, with no authorization or signature check required.

```
┌─────────────────────────────┐
│  Chessnut App (Broker, owns USB)        │
│   UsbClockService (exported bound service) │
└───────┬──────────────┬───────┘
        │ bindService     │ bindService
   ┌──▼─────┐    ┌──▼─────┐
   │ Your App A│    │ Your App B│
   └──────────┘    └──────────┘
```

- Multiple apps can bind **simultaneously** and share the same device.
- Button events are **broadcast** to all registered apps.
- Control commands (e.g. switching the active side) can be sent by any app.

## Prerequisites

- The Chessnut app (application package `com.chessnut.newchessnut`) is installed on the device.
- The Chessnut app has been granted USB permission and is connected to the clock device.
- Your app targets `minSdk >= 24`.

## Integration Steps

### 1. Copy the AIDL files

Copy the following two files into your project **as-is**, keeping the package path `com.chessnut.chessnutnext.clock` unchanged:

```
src/main/aidl/com/chessnut/chessnutnext/clock/IUsbClockService.aidl
src/main/aidl/com/chessnut/chessnutnext/clock/IUsbClockListener.aidl
```

> The package path must exactly match the broker's. Otherwise the AIDL interface descriptor won't match and binding will fail.

### 2. Enable the AIDL build feature

In your app's `build.gradle.kts`:

```kotlin
android {
    buildFeatures {
        aidl = true
    }
}
```

(Groovy DSL: `android { buildFeatures { aidl true } }`)

### 3. Declare package visibility (Android 11+)

Android 11 (API 30) introduced package visibility restrictions. Add the following at the top level of your `AndroidManifest.xml`:

```xml
<queries>
    <package android:name="com.chessnut.newchessnut" />
</queries>
```

Without this, `bindService` may fail silently on Android 11+.

### 4. Bind the service

Bind using an **explicit Intent** (specify both package and action):

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
            // Note: callbacks run on a Binder thread; switch to the main thread to update UI.
        }

        override fun onButtonEvent(side: Int, timestamp: Long) {
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

    // ---- Control commands ----

    /** Connection state: 0=disconnected, 1=connecting, 2=connected */
    fun getConnectionState(): Int = service?.connectionState ?: 0

    /** Switch active side: 0=LEFT, 1=RIGHT */
    fun setActiveSide(side: Int): Boolean = service?.setActiveSide(side) ?: false

    /** Current active side: 0=LEFT, 1=RIGHT, -1=UNKNOWN */
    fun getActiveSide(): Int = service?.activeSide ?: -1
}
```

### 5. Lifecycle

```kotlin
val client = UsbClockClient(context)

// Activity.onStart / when needed
client.bind()

// Activity.onStop / when no longer needed
client.unbind()
```

## API Reference

### IUsbClockService (control interface)

| Method | Description | Returns |
|--------|-------------|---------|
| `registerListener(listener)` | Register an event callback | void |
| `unregisterListener(listener)` | Unregister an event callback | void |
| `getConnectionState()` | Current connection state | `0`=DISCONNECTED, `1`=CONNECTING, `2`=CONNECTED |
| `setActiveSide(side)` | Switch active side (controls LED/indicator) | `boolean` — whether the command was sent |
| `getActiveSide()` | Get the current active side | `0`=LEFT, `1`=RIGHT, `-1`=UNKNOWN |

### IUsbClockListener (event callbacks)

| Method | Parameters | Description |
|--------|-----------|-------------|
| `onConnectionStateChanged(state)` | `state`: 0/1/2 | Connection state changed. The current state is reported once immediately on registration. |
| `onButtonEvent(side, timestamp)` | `side`: 0=LEFT 1=RIGHT; `timestamp`: milliseconds | Active-side change event |
| `onError(error)` | `error`: error description | Error callback |

### Constant Reference

| Meaning | Value |
|---------|-------|
| Button LEFT | `0` |
| Button RIGHT | `1` |
| Active side UNKNOWN | `-1` |
| State DISCONNECTED | `0` |
| State CONNECTING | `1` |
| State CONNECTED | `2` |

## Notes

- **Callback thread**: `IUsbClockListener` callbacks run on a Binder thread pool, not the main thread. Use `Handler(Looper.getMainLooper())` or `runOnUiThread` to update the UI.
- **Event semantics**: The device reports only the current left/right state; it has no separate press/release value. The broker emits `onButtonEvent` only when that state changes. Call `getActiveSide()` whenever you need to synchronize the current state directly.
- **Broker not running**: If the Chessnut app has never started, `BIND_AUTO_CREATE` will start its service. However, the USB permission dialog can only be handled by the Chessnut app, so for first use it's recommended to open the Chessnut app once to grant permission.
- **Multi-app coexistence**: After you unregister or your process exits, `RemoteCallbackList` automatically cleans up your callback without affecting other apps.
- **Auto-reconnect**: The broker internally handles USB auto-reconnect (including recovery from sleep). You only need to listen to `onConnectionStateChanged` to observe state.

## Troubleshooting

| Symptom | Possible cause |
|---------|----------------|
| `bindService` returns false | Missing `<queries>` (Android 11+); or action/package name typo |
| Bound successfully but no events | Forgot `registerListener`; or device not connected (check `getConnectionState`) |
| `setActiveSide` returns false | Device not connected, or `side` is not 0/1 |
| Class not found / cast failure | AIDL file package path doesn't match the broker |
