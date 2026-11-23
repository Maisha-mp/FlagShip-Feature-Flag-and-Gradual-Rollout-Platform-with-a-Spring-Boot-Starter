package com.flagship.starter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;

/**
 * Finds every FlagChangeListener bean in the application and registers it with the store.
 * Runs once all beans exist, so a listener can safely depend on other beans (such as FlagClient).
 */
public final class FlagChangeListenerRegistrar implements SmartInitializingSingleton {

    private final SnapshotStore store;
    private final ObjectProvider<FlagChangeListener> listeners;

    public FlagChangeListenerRegistrar(SnapshotStore store, ObjectProvider<FlagChangeListener> listeners) {
        this.store = store;
        this.listeners = listeners;
    }

    @Override
    public void afterSingletonsInstantiated() {
        listeners.orderedStream().forEach(store::addListener);
    }
}