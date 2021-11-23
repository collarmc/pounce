package com.collarmc.pounce;

/**
 * Cancelable event mixin
 */
public interface Cancelable {
    /**
     * @return event was canceled
     */
    default boolean isCanceled() {
        return CancelableState.isCanceled(this);
    }

    /**
     * Cancel the event from other listeneres
     */
    default void cancel() {
        CancelableState.cancel(this);
    }
}
