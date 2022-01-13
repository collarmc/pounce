package com.collarmc.pounce;

/**
 * Implementors can dispatch events
 */
public interface EventDispatcher {
    /**
     * Dispatch an event
     * @param event to dispatch
     */
    void dispatch(Object event);

    /**
     * Dispatch an event
     * @param event to dispatch
     * @param callback when event is canceled
     */
    void dispatch(Object event, CancelableCallback callback);
}
