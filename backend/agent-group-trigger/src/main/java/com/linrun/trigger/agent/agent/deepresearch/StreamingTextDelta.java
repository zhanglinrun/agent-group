package com.linrun.trigger.agent.agent.deepresearch;

/**
 * 将部分模型返回的累计流式文本转换为本次增量。
 */
final class StreamingTextDelta {

    private String previous = "";

    String apply(String text) {
        String current = text == null ? "" : text;
        if (current.isEmpty()) {
            return "";
        }
        if (current.equals(previous)) {
            return "";
        }
        if (!previous.isEmpty() && current.startsWith(previous)) {
            String delta = current.substring(previous.length());
            previous = current;
            return delta;
        }
        previous = current;
        return current;
    }
}
