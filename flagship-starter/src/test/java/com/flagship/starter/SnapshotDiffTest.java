package com.flagship.starter;

import static com.flagship.starter.TestSnapshots.flag;
import static com.flagship.starter.TestSnapshots.snapshot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.codec.Snapshot;
import com.flagship.codec.SnapshotCodec;
import com.flagship.core.model.Environment;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotDiffTest {

    private final SnapshotCodec codec = new SnapshotCodec(new ObjectMapper());

    private Snapshot parse(String json) {
        return codec.fromJson(json);
    }

    @Test
    void firstLoadReportsEveryFlagAsAddedInKeyOrder() {
        List<FlagChange> changes = SnapshotDiff.between(
                null, parse(snapshot(flag("b", "off"), flag("a", "off"))));

        assertEquals(2, changes.size());
        assertEquals("a", changes.get(0).flagKey());
        assertEquals("b", changes.get(1).flagKey());
        assertEquals(FlagChange.Type.ADDED, changes.get(0).type());
        assertNull(changes.get(0).before());
        assertNotNull(changes.get(0).after());
    }

    @Test
    void identicalSnapshotsGiveNoChanges() {
        String json = snapshot(flag("a", "off"), flag("b", "on"));

        assertTrue(SnapshotDiff.between(parse(json), parse(json)).isEmpty());
    }

    @Test
    void aChangedFlagIsUpdatedAndKeepsBeforeAndAfter() {
        List<FlagChange> changes = SnapshotDiff.between(
                parse(snapshot(flag("a", "off"))), parse(snapshot(flag("a", "on"))));

        assertEquals(1, changes.size());
        FlagChange change = changes.get(0);
        assertEquals(FlagChange.Type.UPDATED, change.type());
        assertEquals("off", change.before().environments().get(Environment.PRODUCTION).defaultVariation());
        assertEquals("on", change.after().environments().get(Environment.PRODUCTION).defaultVariation());
    }

    @Test
    void aMissingFlagIsRemoved() {
        List<FlagChange> changes = SnapshotDiff.between(
                parse(snapshot(flag("a", "off"), flag("b", "off"))), parse(snapshot(flag("a", "off"))));

        assertEquals(1, changes.size());
        assertEquals("b", changes.get(0).flagKey());
        assertEquals(FlagChange.Type.REMOVED, changes.get(0).type());
        assertNotNull(changes.get(0).before());
        assertNull(changes.get(0).after());
    }

    @Test
    void mixedChangesAreSortedByFlagKey() {
        List<FlagChange> changes = SnapshotDiff.between(
                parse(snapshot(flag("a", "off"), flag("b", "off"))),
                parse(snapshot(flag("a", "on"), flag("c", "off"))));

        assertEquals(3, changes.size());
        assertEquals("a", changes.get(0).flagKey());
        assertEquals(FlagChange.Type.UPDATED, changes.get(0).type());
        assertEquals("b", changes.get(1).flagKey());
        assertEquals(FlagChange.Type.REMOVED, changes.get(1).type());
        assertEquals("c", changes.get(2).flagKey());
        assertEquals(FlagChange.Type.ADDED, changes.get(2).type());
    }
}