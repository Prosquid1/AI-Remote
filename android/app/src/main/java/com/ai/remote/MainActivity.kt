package com.ai.remote

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.remote.ai.ServiceLocator
import com.ai.remote.audio.AudioRecorder
import com.ai.remote.audio.LauncherHolder
import com.ai.remote.audio.RecorderUiScreen
import com.ai.remote.audio.Whisper
import com.cactus.CactusContextInitializer
import com.cactus.CactusInitParams
import com.cactus.CactusLM
import com.cactus.CactusSTT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity(), BLEManagerListener {

    private val _startupState = MutableStateFlow<StartupStatus>(StartupStatus.Idle)
    val startupState = _startupState.asStateFlow()

    private lateinit var bleManager: BLEManager

    // Now mutable so UI can update it
    private var targetDeviceName by mutableStateOf("1234")

    // UI state
    private var connectionState by mutableStateOf(ConnectionState.DISCONNECTED)
    private var lastActionMessage by mutableStateOf("Ready to scan.")

    // Permission launcher
    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (granted) {
                startBLEScan()
            } else {
                lastActionMessage = "Bluetooth permissions denied. Cannot connect."
            }
        }

    private lateinit var audioRecorder: AudioRecorder
    private val launcherHolder = LauncherHolder()

    private fun permissionCallback() {
        launcherHolder.permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val granted = permissions[Manifest.permission.RECORD_AUDIO] == true
            audioRecorder.handlePermissionResult(granted)
        }

        audioRecorder = AudioRecorder(this, launcherHolder)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CactusContextInitializer.initialize(this)
        preloadModels()

        // BLE manager
        bleManager = BLEManager(applicationContext)
        bleManager.listener = this

        permissionCallback()
        val whisper = Whisper()

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val state by startupState.collectAsState()

                when (state) {
                    StartupStatus.Idle -> StartupScreen("Starting...")
                    is StartupStatus.Downloading -> StartupScreen("Downloading ${(state as StartupStatus.Downloading).model}...")
                    StartupStatus.Ready -> {
                        BLEChatScreen(
                            connectionState = connectionState,
                            lastActionMessage = lastActionMessage,
                            targetDeviceName = targetDeviceName,

                            // UPDATE DEVICE NAME + CONNECT IMMEDIATELY
                            onTargetNameChanged = { newName ->
                                targetDeviceName = newName
                            },

                            onConnectClicked = {
                                checkPermissionsAndScan()
                            },
                            onDisconnectClicked = {
                                bleManager.disconnect()
                                lastActionMessage = "Manual disconnect requested."
                            },
                            onSendMessage = { message ->
                                handleSendMessage(message)
                            },
                            audioRecorder = audioRecorder,
                            whisper = whisper
                        )
                    }

                    is StartupStatus.Error -> ErrorScreen((state as StartupStatus.Error).message)
                }

            }
        }
    }

    private fun preloadModels() {
        val classifierModel = "smollm2-360m"
        val generatorModel = "lfm2-1.2b"
        val whisperModel = "whisper-small"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Download classifier
                _startupState.value = StartupStatus.Downloading(classifierModel)

                val lm1 = CactusLM()
                lm1.downloadModel(classifierModel)
                lm1.initializeModel(CactusInitParams(classifierModel, contextSize = 512))

                // Download generator
                _startupState.value = StartupStatus.Downloading(generatorModel)

                val lm2 = CactusLM()
                lm2.downloadModel(generatorModel)
                lm2.initializeModel(CactusInitParams(generatorModel, contextSize = 2048))

                // Download generator
                _startupState.value = StartupStatus.Downloading(whisperModel)

                // Download whisper small
                val stt = CactusSTT()
                stt.downloadModel(whisperModel)
                stt.initializeModel(CactusInitParams(whisperModel, contextSize = 2048))

                _startupState.value = StartupStatus.Ready

            } catch (e: Exception) {
                Log.e("AIApp", "Startup error", e)
                _startupState.value = StartupStatus.Error(e.message ?: "Unknown error")
            }
        }
    }

    private fun checkPermissionsAndScan() {
        val permissions =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
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

    // BLEManagerListener
    override fun onConnectionStateChange(state: ConnectionState) {
        connectionState = state
        lastActionMessage = when (state) {
            ConnectionState.DISCONNECTED -> "Disconnected. Tap Connect to retry."
            ConnectionState.SCANNING -> "Scanning for device..."
            ConnectionState.CONNECTING -> "Device found. Connecting/Discovering services..."
            ConnectionState.CONNECTED -> "SUCCESS! Connection established. Ready to chat."
            ConnectionState.ERROR -> "Connection Error. Tap RETRY CONNECT."
        }
    }

    override fun onMessageSent(success: Boolean, message: String) {
        if (success)
            lastActionMessage = "Message successfully sent: '$message'"
        else
            lastActionMessage = "Message send failed: '$message'"
    }

    override fun onMessageReceived(message: String) {
        Log.e("message received: ", message)
    }
}

@Composable
fun StartupScreen(message: String) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Preparing AI Models...", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Text(message)
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ErrorScreen(msg: String) {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Error loading models:", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(msg)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BLEChatScreen(
    connectionState: ConnectionState,
    lastActionMessage: String,
    targetDeviceName: String,
    onTargetNameChanged: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit,
    audioRecorder: AudioRecorder,
    whisper: Whisper
) {
    var messageText by rememberSaveable { mutableStateOf("Open livescore on Google Chrome") }
    var deviceNameText by rememberSaveable { mutableStateOf(targetDeviceName) }
    var isTranscribing by remember { mutableStateOf(false) }

    val color = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }

    val myScope = CoroutineScope(Dispatchers.Main)
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isConnectingOrScanning =
        connectionState == ConnectionState.CONNECTING || connectionState == ConnectionState.SCANNING

    Scaffold(
        topBar = { TopAppBar(title = { Text("BLE Chat") }) }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // STATUS CARD
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Status: ${connectionState.name}",
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    //Spacer(modifier = Modifier.height(4.dp))
                    // Text(lastActionMessage)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // DEVICE NAME FIELD
            Text(
                text = "Enter your Mac Key",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = deviceNameText,
                onValueChange = {
                    deviceNameText = it
                    onTargetNameChanged(it)   // 🔥 Auto-connect
                },
                label = { Text("Enter 4-digit key") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(32.dp))


            // CONNECTION BUTTONS
            renderConnection(
                connectionState,
                isConnected,
                isConnectingOrScanning,
                onConnectClicked,
                onDisconnectClicked
            )

            Spacer(modifier = Modifier.height(32.dp))

            // MESSAGE SEND
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

            if (isTranscribing) {
                LinearProgressIndicator(
                    modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (false) {
                        onSendMessage("messageTextmessageTextmessageTextmessageTextmessageTextmessageTextmessageTextmessageTextmessageTextmessageTextmessageText")
                        return@Button
                    }
                    myScope.launch {
                        val scriptResult = ServiceLocator.router.generateScript(messageText)
                        onSendMessage(scriptResult.script)
                    }

                },
                enabled = isConnected && messageText.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SEND MESSAGE", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            RecorderUiScreen(
                audioRecorder = audioRecorder,
                isEnabled = isConnected,
                whisper = whisper,
                voiceMessage = { voiceMessage ->
                    messageText = voiceMessage
                },
                isTranscribing = { isTranscribing = it }
            )
        }
    }
}



@Composable
fun renderConnection(
    connectionState: ConnectionState,
    isConnected: Boolean,
    isConnectingOrScanning: Boolean,
    onConnectClicked: () -> Unit,
    onDisconnectClicked: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        Button(
            onClick = onConnectClicked,
            enabled = connectionState == ConnectionState.DISCONNECTED || connectionState == ConnectionState.ERROR
        ) {
            Text(if (connectionState == ConnectionState.ERROR) "RETRY CONNECT" else "CONNECT")
        }

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
}

@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF007AFF),
            error = Color(0xFFFF3B30),
            background = Color(0xFFF2F2F7)
        ),
        content = content
    )
}


sealed class StartupStatus {
    object Idle : StartupStatus()
    data class Downloading(val model: String) : StartupStatus()
    object Ready : StartupStatus()
    data class Error(val message: String) : StartupStatus()
}