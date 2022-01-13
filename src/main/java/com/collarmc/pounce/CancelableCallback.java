package com.collarmc.pounce;

@FunctionalInterface
public interface CancelableCallback {
    void canceled(Object event);
}
