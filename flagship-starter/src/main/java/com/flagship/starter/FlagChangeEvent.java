package com.flagship.starter;

import com.flagship.codec.Snapshot;
import java.util.List;

/** Sent to listeners after a refresh that changed at least one flag. */
public record FlagChangeEvent(Snapshot snapshot, List<FlagChange> changes) {

    public FlagChangeEvent {
        changes = List.copyOf(changes);
    }

    /** True if this flag was added, updated or removed. */
    public boolean affects(String flagKey) {
        return changes.stream().anyMatch(c -> c.flagKey().equals(flagKey));
    }
}