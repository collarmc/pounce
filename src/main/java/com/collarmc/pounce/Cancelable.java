package com.collarmc.pounce;

/**
 * Cancelable event mixin
 */
public interface Cancelable {
    /**
     * Cancel the event from other listeneres
     */
    default void cancel() {
        CancelableState.cancel(this);
    }
}
