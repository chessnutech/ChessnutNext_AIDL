package com.chessnut.chessnutnext.clock;

/**
 * Event callback interface for USB clock button events.
 * Callbacks run on a Binder thread pool, not the main thread.
 */
interface IUsbClockListener {
    /**
     * Connection state changed.
     * @param state 0=DISCONNECTED, 1=CONNECTING, 2=CONNECTED
     */
    void onConnectionStateChanged(int state);

    /**
     * Button event received.
     * @param side 0=LEFT, 1=RIGHT
     * @param pressed true if pressed, false if released
     * @param timestamp event timestamp in milliseconds
     */
    void onButtonEvent(int side, boolean pressed, long timestamp);

    /**
     * Error occurred.
     * @param error error description
     */
    void onError(String error);
}
