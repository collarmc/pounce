package com.collarmc.pounce;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of {@link Cancelable}'s
 */
final class CancelableState {

    private static final ConcurrentHashMap<Cancelable, Boolean> canceledState = new ConcurrentHashMap<>();

    public static void cancel(Cancelable cancelable) {
        canceledState.putIfAbsent(cancelable, true);
    }

    public static boolean isCanceled(Cancelable cancelable) {
        Boolean isCancelled = canceledState.get(cancelable);
        return isCancelled != null && isCancelled;
    }

    public static void clear(Cancelable cancelable) {
        canceledState.remove(cancelable);
    }
}
