package com.flagship.starter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SseLineParserTest {

    private final SseLineParser parser = new SseLineParser();

    @Test
    void aCompleteEventHasANameAndData() {
        assertTrue(parser.feed("event:change").isEmpty());
        assertTrue(parser.feed("data:{\"flag\":\"x\"}").isEmpty());

        Optional<SseLineParser.Event> event = parser.feed("");

        assertTrue(event.isPresent());
        assertEquals("change", event.get().name());
        assertEquals("{\"flag\":\"x\"}", event.get().data());
    }

    @Test
    void oneSpaceAfterTheColonIsDropped() {
        parser.feed("event: hello");
        parser.feed("data: {\"version\":0}");

        SseLineParser.Event event = parser.feed("").get();

        assertEquals("hello", event.name());
        assertEquals("{\"version\":0}", event.data());
    }

    @Test
    void commentsLikePingsAreIgnored() {
        assertTrue(parser.feed(":ping").isEmpty());
        assertTrue(parser.feed("").isEmpty());
    }

    @Test
    void severalDataLinesAreJoinedWithANewline() {
        parser.feed("data: one");
        parser.feed("data: two");

        assertEquals("one\ntwo", parser.feed("").get().data());
    }

    @Test
    void anEventWithoutANameIsCalledMessage() {
        parser.feed("data:hi");

        assertEquals("message", parser.feed("").get().name());
    }

    @Test
    void theParserStartsFreshAfterEachEvent() {
        parser.feed("event:change");
        parser.feed("data:first");
        parser.feed("");

        parser.feed("data:second");
        SseLineParser.Event second = parser.feed("").get();

        assertEquals("message", second.name());
        assertEquals("second", second.data());
    }
}