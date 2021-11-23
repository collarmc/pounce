package com.collarmc.pounce;

/**
 * Cancelable event mixin
 */
public interface Cancelable {
    State state = new State();

    /**
     * @return event was canceled
     */
    default boolean isCanceled() {
        return state.isCanceled();
    }

    /**
     * Cancel the event from other listeneres
     */
    default void cancel() {
        state.setCanceled();
    }

    /** manages state of Cancelable mixin **/
    class State {
        private boolean canceled = false;
        public boolean isCanceled() {
            return canceled;
        }
        public void setCanceled() {
            canceled = true;
        }
    }
}
