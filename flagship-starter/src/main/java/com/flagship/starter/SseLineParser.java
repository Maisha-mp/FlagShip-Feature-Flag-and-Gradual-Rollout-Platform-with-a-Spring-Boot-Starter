package com.flagship.starter;

import java.util.Optional;

/**
 * Turns the lines of a Server-Sent Events stream into events.
 * Lines starting with ':' are comments (the server's pings). A blank line ends an event.
 */
final class SseLineParser {

    record Event(String name, String data) { }

    private String name = "message";
    private final StringBuilder data = new StringBuilder();
    private boolean hasData;

    /** Feed one line (without its line break). Returns an event when this line completes one. */
    Optional<Event> feed(String line) {
        if (line.isEmpty()) {
            return dispatch();
        }
        if (line.startsWith(":")) {
            return Optional.empty();
        }

        int colon = line.indexOf(':');
        String field = colon < 0 ? line : line.substring(0, colon);
        String value = colon < 0 ? "" : line.substring(colon + 1);
        if (value.startsWith(" ")) {
            value = value.substring(1);
        }

        switch (field) {
            case "event" -> name = value;
            case "data" -> {
                if (hasData) {
                    data.append('\n');
                }
                data.append(value);
                hasData = true;
            }
            default -> { }   // id, retry and unknown fields are not used
        }
        return Optional.empty();
    }

    private Optional<Event> dispatch() {
        Optional<Event> result = hasData
                ? Optional.of(new Event(name, data.toString()))
                : Optional.empty();
        name = "message";
        data.setLength(0);
        hasData = false;
        return result;
    }
}