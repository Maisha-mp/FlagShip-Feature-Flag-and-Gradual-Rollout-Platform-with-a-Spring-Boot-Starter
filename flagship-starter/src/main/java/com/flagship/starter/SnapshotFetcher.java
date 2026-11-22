package com.flagship.starter;

import java.io.IOException;

/** Gets the snapshot JSON text from somewhere (the server, or a fake in tests). */
@FunctionalInterface
public interface SnapshotFetcher {

    String fetch() throws IOException;
}