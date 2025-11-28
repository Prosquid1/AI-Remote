package com.ai.remote
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Date

class MainActivity : ComponentActivity(), BLEManagerListener {

    private lateinit var bleManager: BLEManager
    private val targetDeviceName = "OKI" // Set your target device name here

    // State managed by the activity, passed to Compose
    private var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
    private var lastActionMessage by mutableStateOf("Ready to scan.")

    // Permission launcher for Android 12+
    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startBLEScan()
            } else {
                lastActionMessage = "Bluetooth permissions denied. Cannot connect."
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BLEManager and set the listener
        bleManager = BLEManager(applicationContext)
        bleManager.listener = this

        setContent {
            MyApplicationTheme {
                BLEChatScreen(
                    connectionState = connectionState,
                    lastActionMessage = lastActionMessage,
                    targetDeviceName = targetDeviceName,
                    onConnectClicked = {
                        checkPermissionsAndScan()
                    },
                    onDisconnectClicked = {
                        bleManager.disconnect()
                        lastActionMessage = "Manual disconnect requested."
                    },
                    onSendMessage = { message ->
                        handleSendMessage(message)
                    }
                )
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions  = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            startBLEScan()
        } else {
            requestBluetoothPermissions.launch(permissions)
        }
    }

    private fun startBLEScan() {
        if (connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR) {
            lastActionMessage = "Starting scan for '$targetDeviceName'..."
            bleManager.startScanning(targetDeviceName)
        } else {
            lastActionMessage = "Please disconnect first or wait for current operation to finish."
        }
    }

    private fun handleSendMessage(message: String) {
        if (connectionState == ConnectionState.CONNECTED) {
            if (bleManager.sendMessage(message)) {
                lastActionMessage = "Sending: '$message'..."
            } else {
                lastActionMessage = "Failed to initiate send."
            }
        } else {
            lastActionMessage = "Cannot send. Not connected."
        }
    }

    // --- BLEManagerListener Implementation ---
    override fun onConnectionStateChange(state: ConnectionState) {
        // Update the state variable used by the Compose UI
        connectionState = state

        // Provide context-specific UI feedback
        lastActionMessage = when (state) {
            ConnectionState.DISCONNECTED -> "Disconnected. Tap Connect to retry."
            ConnectionState.SCANNING -> "Scanning for device..."
            ConnectionState.CONNECTING -> "Device found. Connecting/Discovering services..."
            ConnectionState.CONNECTED -> "SUCCESS! Connection established. Ready to chat."
            ConnectionState.ERROR -> "Connection Error. Tap RETRY CONNECT."
        }
    }

    override fun onMessageSent(success: Boolean, message: String) {
        if (message.equals(PING_MESSAGE)) return
        if (success) {
            lastActionMessage = "Message successfully sent: '$message'"
        } else {
            lastActionMessage = "Message send failed: '$message'"
            //bleManager.disconnect()
        }
    }

    override fun onMessageReceived(message: String) {
        Log.e("message received: " , message)
    }
    // ----------------------------------------
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEChatScreen(
    connectionState: ConnectionState,
    lastActionMessage: String,
    targetDeviceName: String,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    // Local state for the message input field
    var messageText by rememberSaveable { mutableStateOf("Hello from Android") }

    val color = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50) // Green
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFC107) // Amber/Yellow
        else -> Color(0xFFF44336) // Red/Error
    }

    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnectingOrScanning = connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.SCANNING

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("BLE Chat: $targetDeviceName") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Display Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Status: ${connectionState.name}",
                        fontWeight = FontWeight.Bold,
                        color = color,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = lastActionMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isConnectingOrScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(8.dp))
            }


            // Connection Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Connect/Reconnect Button
                Button(
                    onClick = onConnectClicked,
                    enabled = connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (connectionState == ConnectionState.ERROR) "RETRY CONNECT" else "CONNECT")
                }

                // Disconnect Button
                Button(
                    onClick = onDisconnectClicked,
                    enabled = isConnected || isConnectingOrScanning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("DISCONNECT")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Divider()
            Spacer(modifier = Modifier.height(32.dp))

            // Message Sending Section
            Text(
                text = "Send Data",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                label = { Text("Message to Send") },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected
            )
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onSendMessage(messageText) },
                enabled = isConnected && messageText.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SEND MESSAGE", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

// Dummy Theme for compilation
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF007AFF), // iOS Blue
            error = Color(0xFFFF3B30),
            background = Color(0xFFF2F2F7)
        ),
        content = content
    )
}