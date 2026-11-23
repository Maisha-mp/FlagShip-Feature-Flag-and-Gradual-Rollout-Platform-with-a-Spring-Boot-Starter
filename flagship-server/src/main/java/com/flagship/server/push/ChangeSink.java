package com.flagship.server.push;

/** Where change events are delivered (one connected app). */
public interface ChangeSink {

    /** Sends one event. Returns false if the receiver is gone. */
    boolean send(String eventName, String json);

    /** Sends a keep-alive. Returns false if the receiver is gone. */
    boolean ping();
}