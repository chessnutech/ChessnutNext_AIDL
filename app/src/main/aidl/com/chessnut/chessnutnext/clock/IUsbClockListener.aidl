package com.chessnut.chessnutnext.clock;

/**
 * Event callback interface for USB clock connection and active-side events.
 * Callbacks run on a Binder thread pool, not the main thread.
 */
interface IUsbClockListener {
    /**
     * Connection state changed.
     * @param state 0=DISCONNECTED, 1=CONNECTING, 2=CONNECTED
     */
    void onConnectionStateChanged(int state);

    /**
     * Active-side change event received.
     * @param side 0=LEFT, 1=RIGHT
     * @param timestamp event timestamp in milliseconds
     */
    void onButtonEvent(int side, long timestamp);

    /**
     * Error occurred.
     * @param error error description
     */
    void onError(String error);
}
