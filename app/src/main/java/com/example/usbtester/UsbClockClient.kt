package com.example.usbtester

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.chessnut.chessnutnext.clock.IUsbClockListener
import com.chessnut.chessnutnext.clock.IUsbClockService

/**
 * Client for connecting to Chessnut USB Clock Service via AIDL.
 */
class UsbClockClient(private val context: Context) {

    companion object {
        private const val TAG = "UsbClockClient"
        private const val BROKER_PACKAGE = "com.chessnut.newchessnut"
        private const val BIND_ACTION = "com.chessnut.chessnutnext.clock.UsbClockService"

        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTING = 1
        const val STATE_CONNECTED = 2

        const val SIDE_LEFT = 0
        const val SIDE_RIGHT = 1
        const val SIDE_UNKNOWN = -1
    }

    private var service: IUsbClockService? = null
    private var bound = false

    // Callback interface for event notifications
    interface EventCallback {
        fun onConnectionStateChanged(state: Int)
        fun onButtonEvent(side: Int, timestamp: Long)
        fun onError(error: String)
    }

    private var eventCallback: EventCallback? = null

    private val listener = object : IUsbClockListener.Stub() {
        override fun onConnectionStateChanged(state: Int) {
            Log.d(TAG, "Connection state changed: $state")
            eventCallback?.onConnectionStateChanged(state)
        }

        override fun onButtonEvent(side: Int, timestamp: Long) {
            Log.d(TAG, "Active side changed - side: $side, timestamp: $timestamp")
            eventCallback?.onButtonEvent(side, timestamp)
        }

        override fun onError(error: String?) {
            Log.e(TAG, "Error: $error")
            eventCallback?.onError(error ?: "Unknown error")
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.d(TAG, "Service connected: $name")
            try {
                service = IUsbClockService.Stub.asInterface(binder).also {
                    it.registerListener(listener)
                    Log.d(TAG, "Listener registered")
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "Failed to register listener", e)
                eventCallback?.onError("Failed to register listener: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service disconnected: $name")
            service = null
            eventCallback?.onConnectionStateChanged(STATE_DISCONNECTED)
        }
    }

    /**
     * Set callback for receiving events.
     */
    fun setEventCallback(callback: EventCallback?) {
        this.eventCallback = callback
    }

    /**
     * Bind to the USB Clock Service.
     * @return true if binding was initiated successfully
     */
    fun bind(): Boolean {
        if (bound) {
            Log.w(TAG, "Already bound")
            return true
        }

        val intent = Intent(BIND_ACTION).apply {
            setPackage(BROKER_PACKAGE)
        }

        return try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            Log.d(TAG, "Bind service result: $bound")
            bound
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind service", e)
            eventCallback?.onError("Failed to bind service: ${e.message}")
            false
        }
    }

    /**
     * Unbind from the USB Clock Service.
     */
    fun unbind() {
        if (!bound) {
            return
        }

        try {
            service?.unregisterListener(listener)
            Log.d(TAG, "Listener unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister listener", e)
        }

        try {
            context.unbindService(connection)
            Log.d(TAG, "Service unbound")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind service", e)
        }

        service = null
        bound = false
    }

    // ---- Control commands ----

    /**
     * Get current connection state.
     * @return 0=DISCONNECTED, 1=CONNECTING, 2=CONNECTED
     */
    fun getConnectionState(): Int {
        return try {
            service?.connectionState ?: STATE_DISCONNECTED
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get connection state", e)
            STATE_DISCONNECTED
        }
    }

    /**
     * Switch active side (controls LED/indicator).
     * @param side 0=LEFT, 1=RIGHT
     * @return true if command was sent successfully
     */
    fun setActiveSide(side: Int): Boolean {
        return try {
            service?.setActiveSide(side) ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to set active side", e)
            eventCallback?.onError("Failed to set active side: ${e.message}")
            false
        }
    }

    /**
     * Get the current active side.
     * @return 0=LEFT, 1=RIGHT, -1=UNKNOWN
     */
    fun getActiveSide(): Int {
        return try {
            service?.activeSide ?: SIDE_UNKNOWN
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get active side", e)
            SIDE_UNKNOWN
        }
    }

    /**
     * Check if currently bound to the service.
     */
    fun isBound(): Boolean = bound

    /**
     * Check if service interface is available.
     */
    fun isServiceAvailable(): Boolean = service != null
}
