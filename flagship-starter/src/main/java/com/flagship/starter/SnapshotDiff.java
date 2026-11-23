package com.flagship.starter;

import com.flagship.codec.Snapshot;
import com.flagship.core.model.Flag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Works out which flags differ between two snapshots. */
final class SnapshotDiff {

    private SnapshotDiff() { }

    /** before may be null (first load: every flag counts as added). Result is sorted by flag key. */
    static List<FlagChange> between(Snapshot before, Snapshot after) {
        Map<String, Flag> old = before == null ? Map.of() : before.flags();
        Map<String, Flag> now = after.flags();

        List<FlagChange> changes = new ArrayList<>();
        for (Map.Entry<String, Flag> e : now.entrySet()) {
            Flag previous = old.get(e.getKey());
            if (previous == null) {
                changes.add(new FlagChange(e.getKey(), FlagChange.Type.ADDED, null, e.getValue()));
            } else if (!previous.equals(e.getValue())) {   // Flag is a record, so this compares everything inside
                changes.add(new FlagChange(e.getKey(), FlagChange.Type.UPDATED, previous, e.getValue()));
            }
        }
        for (Map.Entry<String, Flag> e : old.entrySet()) {
            if (!now.containsKey(e.getKey())) {
                changes.add(new FlagChange(e.getKey(), FlagChange.Type.REMOVED, e.getValue(), null));
            }
        }
        changes.sort(Comparator.comparing(FlagChange::flagKey));
        return List.copyOf(changes);
    }
}