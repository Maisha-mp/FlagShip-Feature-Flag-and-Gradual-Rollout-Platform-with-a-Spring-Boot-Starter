package com.flagship.starter;

/** Implement this to be told when flags change (the Observer in the Observer pattern). */
@FunctionalInterface
public interface FlagChangeListener {

    /**
     * Called on the thread that refreshed the snapshot, so keep it quick.
     * An exception thrown here is logged and does not affect other listeners.
     */
    void onFlagsChanged(FlagChangeEvent event);
}