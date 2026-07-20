package com.example.usbtester

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.usbtester.ui.theme.USBTesterTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var usbClockClient: UsbClockClient
    private val mainHandler = Handler(Looper.getMainLooper())

    // UI state
    private var isServiceBound by mutableStateOf(false)
    private var isServiceAvailable by mutableStateOf(false)
    private var connectionState by mutableStateOf(-1)
    private var eventLog by mutableStateOf<List<String>>(emptyList())
    private var lastLeftButtonState by mutableStateOf(false)
    private var lastRightButtonState by mutableStateOf(false)
    private var activeSide by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        usbClockClient = UsbClockClient(this).apply {
            setEventCallback(object : UsbClockClient.EventCallback {
                override fun onConnectionStateChanged(state: Int) {
                    mainHandler.post {
                        connectionState = state
                        addLog("Connection state changed: ${stateToString(state)}")
                    }
                }

                override fun onButtonEvent(side: Int, pressed: Boolean, timestamp: Long) {
                    mainHandler.post {
                        val sideStr = if (side == UsbClockClient.SIDE_LEFT) "LEFT" else "RIGHT"
                        val action = if (pressed) "PRESSED" else "RELEASED"
                        addLog("Button $sideStr $action at ${formatTimestamp(timestamp)}")

                        // Update button state
                        if (side == UsbClockClient.SIDE_LEFT) {
                            lastLeftButtonState = pressed
                        } else {
                            lastRightButtonState = pressed
                        }
                    }
                }

                override fun onError(error: String) {
                    mainHandler.post {
                        addLog("ERROR: $error")
                    }
                }
            })
        }

        setContent {
            USBTesterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TestScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isServiceBound) {
            bindService()
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService()
    }

    private fun bindService() {
        addLog("Attempting to bind service...")
        val result = usbClockClient.bind()
        isServiceBound = result
        addLog(if (result) "Service bind initiated" else "Service bind failed")

        // Check service availability after a short delay
        mainHandler.postDelayed({
            isServiceAvailable = usbClockClient.isServiceAvailable()
            if (isServiceAvailable) {
                connectionState = usbClockClient.getConnectionState()
                addLog("Service available, connection state: ${stateToString(connectionState)}")
            }
        }, 500)
    }

    private fun unbindService() {
        if (isServiceBound) {
            addLog("Unbinding service...")
            usbClockClient.unbind()
            isServiceBound = false
            isServiceAvailable = false
            connectionState = -1
        }
    }

    private fun connectDevice() {
        addLog("Requesting device connection...")
        val result = usbClockClient.connect()
        addLog(if (result) "Connect command sent" else "Connect command failed")
    }

    private fun switchSide(side: Int) {
        val sideStr = if (side == UsbClockClient.SIDE_LEFT) "LEFT" else "RIGHT"
        addLog("Switching active side to $sideStr...")
        val result = usbClockClient.setActiveSide(side)
        if (result) {
            activeSide = side
            addLog("Active side set to $sideStr")
        } else {
            addLog("Failed to set active side")
        }
    }

    private fun queryState() {
        val state = usbClockClient.getConnectionState()
        connectionState = state
        addLog("Query result: ${stateToString(state)}")
    }

    private fun clearLog() {
        eventLog = emptyList()
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        eventLog = eventLog + "[$timestamp] $message"
    }

    private fun stateToString(state: Int): String = when (state) {
        UsbClockClient.STATE_DISCONNECTED -> "DISCONNECTED (0)"
        UsbClockClient.STATE_CONNECTING -> "CONNECTING (1)"
        UsbClockClient.STATE_CONNECTED -> "CONNECTED (2)"
        -1 -> "UNKNOWN (-1)"
        else -> "INVALID ($state)"
    }

    private fun formatTimestamp(timestamp: Long): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
    }

    @Composable
    fun TestScreen() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Title
            Text(
                text = "USB Chess Clock Tester",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Status Card
            StatusCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Button States
            ButtonStatesCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons
            ControlButtonsCard()

            Spacer(modifier = Modifier.height(16.dp))

            // Event Log
            EventLogCard()

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    @Composable
    fun StatusCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Service Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                StatusRow("Service Bound", isServiceBound)
                StatusRow("Service Available", isServiceAvailable)

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Connection State:")
                    Text(
                        text = stateToString(connectionState),
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            UsbClockClient.STATE_CONNECTED -> Color(0xFF4CAF50)
                            UsbClockClient.STATE_CONNECTING -> Color(0xFFFFA726)
                            else -> Color(0xFFF44336)
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun StatusRow(label: String, value: Boolean) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label:")
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(
                        color = if (value) Color(0xFF4CAF50) else Color(0xFFF44336),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }

    @Composable
    fun ButtonStatesCard() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Button States",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ButtonStateIndicator("LEFT", lastLeftButtonState, activeSide == UsbClockClient.SIDE_LEFT)
                    ButtonStateIndicator("RIGHT", lastRightButtonState, activeSide == UsbClockClient.SIDE_RIGHT)
                }
            }
        }
    }

    @Composable
    fun ButtonStateIndicator(label: String, pressed: Boolean, isActive: Boolean) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        color = when {
                            pressed -> Color(0xFF4CAF50)
                            isActive -> Color(0xFFFFA726)
                            else -> Color(0xFFE0E0E0)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (pressed || isActive) Color.White else Color.Black
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (pressed) "PRESSED" else "RELEASED",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }

    @Composable
    fun ControlButtonsCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Service control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { bindService() },
                        modifier = Modifier.weight(1f),
                        enabled = !isServiceBound
                    ) {
                        Text("Bind")
                    }
                    Button(
                        onClick = { unbindService() },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceBound
                    ) {
                        Text("Unbind")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Device control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { connectDevice() },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceAvailable
                    ) {
                        Text("Connect")
                    }
                    Button(
                        onClick = { queryState() },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceAvailable
                    ) {
                        Text("Query State")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Side switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { switchSide(UsbClockClient.SIDE_LEFT) },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceAvailable && connectionState == UsbClockClient.STATE_CONNECTED
                    ) {
                        Text("Active ← LEFT")
                    }
                    Button(
                        onClick = { switchSide(UsbClockClient.SIDE_RIGHT) },
                        modifier = Modifier.weight(1f),
                        enabled = isServiceAvailable && connectionState == UsbClockClient.STATE_CONNECTED
                    ) {
                        Text("Active → RIGHT")
                    }
                }
            }
        }
    }

    @Composable
    fun EventLogCard() {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Event Log (${eventLog.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { clearLog() }) {
                        Text("Clear")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (eventLog.isEmpty()) {
                    Text(
                        text = "No events yet...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    ) {
                        eventLog.takeLast(50).reversed().forEach { log ->
                            Text(
                                text = log,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
