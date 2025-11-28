package com.ai.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.UUID

// --- New Definitions for State Communication ---

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

interface BLEManagerListener {
    fun onConnectionStateChange(state: ConnectionState)
    fun onMessageSent(success: Boolean, message: String)
    fun onMessageReceived(message: String)
}

// ---------------------------------------------


class BLEManager(private val context: Context) {
    private val bluetoothAdapter: BluetoothAdapter? =
        BluetoothAdapter.getDefaultAdapter()

    var writeInProgress = false
    private val bleScanner = bluetoothAdapter?.bluetoothLeScanner

    private val pingMessage = "PING!"

    // --- Keep-Alive and Queue Properties ---
    private val KEEPALIVE_INTERVAL_MS = 15000L // Ping every 15 seconds
    private val handler = Handler(Looper.getMainLooper())
    // ---------------------------------------

    // --- Listener for UI updates ---
    var listener: BLEManagerListener? = null
    // -------------------------------

    val serviceUUID = "12345678-1234-1234-1234-1234567890AB"
    val characteristicUUID = "87654321-4321-4321-4321-BA0987654321"

    private lateinit var targetDeviceName: String

    // private var isConnecting = false;

    @SuppressLint("MissingPermission")
    private var connectedGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null

    private var currentState: ConnectionState = ConnectionState.DISCONNECTED
        set(value) {
            field = value
            listener?.onConnectionStateChange(value)
        }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name == targetDeviceName) {
                    Log.d("BLE", "Found target device: ${device.name}")
                    stopScanning()
                    currentState = ConnectionState.CONNECTING // Update state
                    connectToDevice(device)
                } else {
                    //isConnecting = false;
                    Log.d("BLE", "Found other device: ${device.name}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLE", "Scan failed with error: $errorCode")
            currentState = ConnectionState.ERROR // Update state
        }
    }


    @SuppressLint("MissingPermission")
    fun startScanning(targetDeviceName: String) {
        if (currentState == ConnectionState.SCANNING || currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING) {
            Log.w("BLE", "Already scanning or connected. Aborting new scan.")
            return
        }

        this.targetDeviceName = targetDeviceName

        // Permission check omitted for brevity in this file, assume granted in MainActivity

        val filter = ScanFilter.Builder()
            .setDeviceName(targetDeviceName)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(listOf(filter), settings, scanCallback)
            currentState = ConnectionState.SCANNING // Update state
            Log.d("BLE", "Started scanning")
        } catch (e: Exception) {
            // isConnecting = false
            Log.e("BLE", "Scan error: ${e.localizedMessage}")
            currentState = ConnectionState.ERROR // Update state
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        try {
            // isConnecting = false;
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e("BLE", "Stop scan error: ${e.localizedMessage}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        Log.d("BLE", "Connecting to device: ${device.address}")
        device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun sendMessage(message: String, isPing: Boolean = false): Boolean {
        val gatt = connectedGatt
        val characteristic = writeCharacteristic

        if (gatt == null || characteristic == null) {
            Log.e("BLE", "Cannot send message: Not connected or characteristic not found.")
            listener?.onMessageSent(false, message)
            return false
        }

        if (!isPing) {
            // Reset keep alive implementation
            stopKeepAlive()
            startKeepAlive()
        }

        return try {
            val messageBytes = message.toByteArray()
            val success = writeInPackets(gatt, characteristic, messageBytes)
            Log.d("BLE", "Write initiated for message: '$message'. Success status: $success")

            // Wait for onCharacteristicWrite for final status, but return initiation status now
            success
        } catch (e: Exception) {
            Log.e("BLE", "Error writing characteristic: ${e.message}")
            listener?.onMessageSent(false, message)
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeInPackets(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray,
        packetSize: Int = 20
    ): Boolean {
        var offset = 0

        while (offset < data.size) {
            val end = minOf(offset + packetSize, data.size)
            val packet = data.copyOfRange(offset, end)

            characteristic.value = packet
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

            writeInProgress = true
            val success = gatt.writeCharacteristic(characteristic)

            if (!success) {
                Log.e("BLE", "Failed to write packet at offset $offset")
                return false
            }

            offset = end
        }

        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        connectedGatt?.let {
            it.disconnect()
        }
    }

    // ----------------------------------------------------
    // KEEP-ALIVE (PING) IMPLEMENTATION
    // ----------------------------------------------------

    // We will use a read operation on a common characteristic as the ping.
    // Replace with a characteristic from the peripheral (e.g., Battery Service, Device Name)
    // If you don't know one, this will read the last service discovered.
    @SuppressLint("MissingPermission")
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            val gatt = connectedGatt
            // Example: Try reading the first characteristic of the first discovered service
            // NOTE: You should use a known, benign characteristic UUID here
            val targetChar = gatt?.services?.firstOrNull()
                ?.characteristics?.firstOrNull()

            if (gatt != null && targetChar != null) {
                // Performing a write operation to ping the host
                val success = sendMessage(PING_MESSAGE)
                if (!success) {
                    Log.w("BLE", "Keep-alive read failed to initiate.")
                } else {
                    Log.d("BLE", "Keep-alive ping sent (read operation).")
                }
                // Reschedule the ping
                handler.postDelayed(this, KEEPALIVE_INTERVAL_MS)
            } else {
                Log.w("BLE", "Keep-alive terminated: GATT or characteristic not available.")
                stopKeepAlive()
            }
        }
    }

    private fun stopKeepAlive() {
        handler.removeCallbacks(keepAliveRunnable)
        Log.d("BLE", "Keep-alive stopped.")
    }

    private fun startKeepAlive() {
        handler.postDelayed(keepAliveRunnable, KEEPALIVE_INTERVAL_MS)
        Log.d("BLE", "Keep-alive started with ${KEEPALIVE_INTERVAL_MS}ms interval.")
    }


    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d("BLE", "Connected to GATT server. Discovering services...")
                    connectedGatt = gatt
                    currentState = ConnectionState.CONNECTING // Still 'connecting' until services discovered

                    if (refreshDeviceCache(gatt)) {
                        Log.d("BLE", "GATT cache cleared successfully. Discovering services...")
                        gatt?.discoverServices()
                    } else {
                        Log.e("BLE", "Failed to clear GATT cache. Proceeding with discovery.")
                        gatt?.discoverServices()
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d("BLE", "Disconnected from GATT server (Status: $status)")
                    gatt?.close()
                    // isConnecting = false;
                    connectedGatt = null
                    stopKeepAlive()
                    writeCharacteristic = null
                    currentState = ConnectionState.DISCONNECTED // Final state update
                }
            }

        }

        // Java reflections
        private fun refreshDeviceCache(gatt: BluetoothGatt?): Boolean {
            try {
                val refreshMethod = gatt?.javaClass?.getMethod("refresh")
                refreshMethod?.let {
                    val success = it.invoke(gatt) as Boolean
                    Log.d("BLE", "Calling refresh() method on BluetoothGatt. Success: $success")
                    return success
                }
            } catch (e: Exception) {
                Log.e("BLE", "Error clearing GATT cache: ${e.message}")
            }
            return false
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {

                val service = gatt?.getService(UUID.fromString(serviceUUID))
                val characteristic = service?.getCharacteristic(UUID.fromString(characteristicUUID))

                if (service != null && characteristic != null) {
                    writeCharacteristic = characteristic
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    currentState = ConnectionState.CONNECTED // Final success state

                    val notifEnabled = enableNotifications(gatt, characteristic)
                    Log.d("BLE", "Notifications enabled: $notifEnabled")
                    Log.d("BLE", "Service and Characteristic found. Ready to send messages.")

                    startKeepAlive()
                } else {
                    Log.e("BLE", "Service or Characteristic not found. Disconnecting.")
                    // If discovery fails, explicitly disconnect to clean up the partially connected state
                    gatt?.disconnect()
                    currentState = ConnectionState.ERROR // Final error state
                }

            } else {
                Log.e("BLE", "Service discovery failed: $status")
                gatt?.disconnect()
                currentState = ConnectionState.ERROR // Final error state
            }
            // isConnecting = false;
        }

        @SuppressLint("MissingPermission")
        fun readOnce(): Boolean {
            val gatt = connectedGatt
            val characteristic = writeCharacteristic // or a separate read characteristic

            if (gatt == null || characteristic == null) {
                Log.e("BLE", "Cannot read: Not connected or characteristic not found.")
                return false
            }

            return gatt.readCharacteristic(characteristic)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic.value
                val message = data?.toString(Charsets.UTF_8) ?: ""
                Log.d("BLE", "Read response: $message")
                listener?.onMessageReceived(message)
            } else {
                Log.e("BLE", "Characteristic read failed with status: $status")
            }
        }


        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            val message = data?.toString(Charsets.UTF_8) ?: ""

            Log.d("BLE", "Notification received: $message")

            // Ignore PING messages if you only care about real payloads
            if (message == pingMessage) return

            listener?.onMessageReceived(message)
        }

        @SuppressLint("MissingPermission")
        private fun enableNotifications(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ): Boolean {
            // Enable local notifications
            val success = gatt.setCharacteristicNotification(characteristic, true)
            if (!success) {
                Log.e("BLE", "setCharacteristicNotification failed")
                return false
            }

            // Enable notifications on the peripheral via CCCD descriptor
            val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            val descriptor = characteristic.getDescriptor(cccdUuid)

            if (descriptor == null) {
                Log.e("BLE", "CCCD descriptor not found")
                return false
            }

            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = gatt.writeDescriptor(descriptor)

            if (!writeSuccess) {
                Log.e("BLE", "Failed to write CCCD descriptor")
            }

            return writeSuccess
        }


        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeInProgress = false
            val success = status == BluetoothGatt.GATT_SUCCESS
            val message = characteristic.value?.toString(Charsets.UTF_8) ?: "Unknown Message"
            if (message.equals(PING_MESSAGE)) return
            if (success) {
                Log.d("BLE", "Characteristic write successful.")
            } else {
                Log.e("BLE", "Characteristic write failed with status: $status")
            }
            listener?.onMessageSent(success, message)
        }
    }
}