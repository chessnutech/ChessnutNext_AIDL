package com.chessnut.chessnutnext.clock;

import com.chessnut.chessnutnext.clock.IUsbClockListener;

/**
 * Control interface for USB chess clock.
 */
interface IUsbClockService {
    /**
     * Register a listener for connection and active-side events.
     */
    void registerListener(IUsbClockListener listener);

    /**
     * Unregister a previously registered listener.
     */
    void unregisterListener(IUsbClockListener listener);

    /**
     * Get current connection state.
     * @return 0=DISCONNECTED, 1=CONNECTING, 2=CONNECTED
     */
    int getConnectionState();

    /**
     * Switch the active side (controls LED/indicator).
     * @param side 0=LEFT, 1=RIGHT
     * @return true if command was sent successfully
     */
    boolean setActiveSide(int side);

    /**
     * Get the current active side.
     * @return 0=LEFT, 1=RIGHT, -1=UNKNOWN
     */
    int getActiveSide();
}
