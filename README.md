# USB Chess Clock Tester

A test application for verifying the Chessnut USB Chess Clock AIDL interface integration.

## Features

This app tests all aspects of the USB Chess Clock AIDL interface:

- ✅ Service binding/unbinding
- ✅ Connection state monitoring
- ✅ Active-side change callbacks (LEFT/RIGHT)
- ✅ Active side switching
- ✅ Real-time event logging
- ✅ Visual active-side indicators

## Prerequisites

1. **ChessnutNext App**: ChessnutNext version **0.5.11 or later** (`com.chessnut.newchessnut`) must be installed on your device
2. **USB Device**: The Chessnut USB chess clock must be connected and have USB permission granted in the Chessnut app
3. **Android Version**: Android 11+ (API 30+) recommended, minimum SDK 24

## Project Structure

```
app/
├── src/main/
│   ├── aidl/com/chessnut/chessnutnext/clock/
│   │   ├── IUsbClockService.aidl      # Service control interface
│   │   └── IUsbClockListener.aidl     # Event callback interface
│   ├── java/com/example/usbtester/
│   │   ├── MainActivity.kt             # Test UI with Jetpack Compose
│   │   └── UsbClockClient.kt          # AIDL client wrapper
│   └── AndroidManifest.xml            # Includes <queries> for package visibility
└── build.gradle.kts                   # AIDL enabled
```

## How to Use

### 1. Install and Setup

```bash
# Build and install the app
./gradlew installDebug

# Or open in Android Studio and run
```

### 2. Using the Test App

The app automatically binds to the service when started. The UI provides:

#### **Service Status Card**
- Shows service binding state
- Shows service availability
- Displays current connection state (DISCONNECTED/CONNECTING/CONNECTED)

#### **Active Side Card**
- Visual indicators for LEFT and RIGHT sides
- Shows the current active side in real-time
- Supports an UNKNOWN state before synchronization

#### **Controls**
- **Bind/Unbind**: Manually control service binding
- **Query Connection & Active Side**: Query the current connection and active-side states
- **Active LEFT/RIGHT**: Switch the active side (controls LED indicator)

#### **Event Log**
- Real-time log of all events with timestamps
- Shows connection state changes
- Shows active-side change events
- Shows errors
- Displays up to 50 most recent events
- **Clear** button to reset the log

### 3. Testing Flow

1. **Initial Setup**
   - Launch the app → it will automatically bind to the service
   - Check "Service Bound" and "Service Available" indicators turn green

2. **Wait for Device Connection**
   - The ChessnutNext broker manages the USB connection automatically
   - Watch connection state change: DISCONNECTED → CONNECTING → CONNECTED
   - Check the event log for state changes

3. **Test Active-Side Events**
   - Change the chess clock to LEFT → should see "Active side changed to LEFT" in the log
   - Change the chess clock to RIGHT → should see "Active side changed to RIGHT" in the log
   - The visual active-side indicator should update in real time

4. **Test Active Side Switching**
   - Tap **Active ← LEFT** → should see active side indicator highlight LEFT
   - Tap **Active → RIGHT** → should see active side indicator highlight RIGHT
   - LED on the physical device should switch accordingly

5. **Test State Query**
   - Tap **Query Connection & Active Side** → both current states appear in the log

## API Testing Coverage

### IUsbClockService Methods

| Method | Test Coverage |
|--------|---------------|
| `registerListener()` | ✅ Automatically called on bind |
| `unregisterListener()` | ✅ Automatically called on unbind |
| `getConnectionState()` | ✅ State query + initial synchronization |
| `setActiveSide(side)` | ✅ Active LEFT/RIGHT buttons |
| `getActiveSide()` | ✅ State query + initial synchronization |

### IUsbClockListener Callbacks

| Callback | Test Coverage |
|----------|---------------|
| `onConnectionStateChanged(state)` | ✅ Logged with state name |
| `onButtonEvent(side, timestamp)` | ✅ Logged with formatted timestamp and active-side indicator |
| `onError(error)` | ✅ Logged with ERROR prefix |

## Troubleshooting

### Service Bind Failed

**Symptom**: "Service bind failed" in log

**Solutions**:
- Verify Chessnut app is installed: `adb shell pm list packages | grep chessnut`
- Check `<queries>` declaration in AndroidManifest.xml (required for Android 11+)
- Try opening the Chessnut app first to initialize it

### Service Bound but Not Available

**Symptom**: "Service Bound" is green but "Service Available" is red

**Solutions**:
- The service may still be initializing → wait a few seconds
- Try manually tapping **Unbind** then **Bind**
- Check logcat for detailed error messages: `adb logcat -s UsbClockClient`

### No Active-Side Events

**Symptom**: Connection state is CONNECTED but no active-side change events appear

**Solutions**:
- Verify USB device is physically connected
- Check USB permission was granted in the Chessnut app
- Try disconnecting and reconnecting the USB device
- Open the Chessnut app to verify it can receive active-side changes

### Connection State Stays DISCONNECTED

**Symptom**: The state remains DISCONNECTED

**Solutions**:
- USB device may not be connected
- USB permission may not be granted → open Chessnut app first
- Try unplugging and replugging the USB device
- Check Chessnut app settings

## Development Notes

### Threading

- All AIDL callbacks run on **Binder thread pool** (not main thread)
- `MainActivity` uses `Handler(Looper.getMainLooper())` to post events to the main thread
- MainActivity's callback interface receives events on main thread → safe for UI updates

### AIDL Package Path

The AIDL files **must** maintain the exact package path `com.chessnut.chessnutnext.clock`:
- This matches the broker app's AIDL package
- Changing the package will cause interface descriptor mismatch
- Binding will fail silently if package doesn't match

### Multiple App Support

The broker service supports multiple apps binding simultaneously:
- All registered apps receive the same active-side change events
- Any app can send control commands
- Apps don't interfere with each other

## Logcat Debugging

```bash
# View all USB tester logs
adb logcat -s UsbClockClient

# View both tester and broker logs
adb logcat -s UsbClockClient:D UsbClockService:D

# Clear and watch live
adb logcat -c && adb logcat -s UsbClockClient:D
```

## Known Limitations

- The device reports only the current LEFT/RIGHT state, not separate press/release values
- The broker emits `onButtonEvent` only when the active side changes
- Event log limited to 50 most recent entries to prevent memory issues
- Requires Chessnut app to be installed (cannot test without broker)

## Integration Reference

For integrating this AIDL interface into your own app, refer to:
- [UsbClockClient.kt](app/src/main/java/com/example/usbtester/UsbClockClient.kt) - Complete client implementation
- AIDL files in `app/src/main/aidl/` - Interface definitions
- [AndroidManifest.xml](app/src/main/AndroidManifest.xml) - Required `<queries>` declaration

## License

This is a test/sample application for integration testing purposes.
