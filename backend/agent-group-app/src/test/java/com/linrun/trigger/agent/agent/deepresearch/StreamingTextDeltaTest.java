package com.linrun.trigger.agent.agent.deepresearch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamingTextDeltaTest {

    @Test
    void convertsCumulativeChunksToDeltas() {
        StreamingTextDelta delta = new StreamingTextDelta();

        assertEquals("大模型", delta.apply("大模型"));
        assertEquals("发展", delta.apply("大模型发展"));
        assertEquals("历程", delta.apply("大模型发展历程"));
    }

    @Test
    void keepsPlainDeltaChunksUnchanged() {
        StreamingTextDelta delta = new StreamingTextDelta();

        assertEquals("大模型", delta.apply("大模型"));
        assertEquals("发展", delta.apply("发展"));
    }

    @Test
    void ignoresRepeatedCumulativeChunk() {
        StreamingTextDelta delta = new StreamingTextDelta();

        assertEquals("abc", delta.apply("abc"));
        assertEquals("", delta.apply("abc"));
    }
}
