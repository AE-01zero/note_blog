package com.aezer0.initialization.service.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits model stream chunks into answer text and explicit thinking-tag text.
 */
public class AiStreamChunkParser {

    private static final String THINK_OPEN = "<think>";
    private static final String THINKING_OPEN = "<thinking>";
    private static final String THINK_CLOSE = "</think>";
    private static final String THINKING_CLOSE = "</thinking>";
    private static final int OPEN_TAIL = THINKING_OPEN.length() - 1;

    private final StringBuilder buffer = new StringBuilder();
    private boolean inThinking;
    private boolean thinkingStarted;

    public List<ParsedChunk> accept(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }
        buffer.append(chunk);
        return drain(false);
    }

    public List<ParsedChunk> finish() {
        return drain(true);
    }

    private List<ParsedChunk> drain(boolean finish) {
        List<ParsedChunk> result = new ArrayList<>();

        while (!buffer.isEmpty()) {
            if (inThinking) {
                CloseMatch close = findCloseTag(buffer);
                if (close != null) {
                    appendContent(result, ChunkType.THINKING, buffer.substring(0, close.index()));
                    buffer.delete(0, close.index() + close.tag().length());
                    inThinking = false;
                    result.add(new ParsedChunk(ChunkType.THINKING_DONE, ""));
                    continue;
                }

                int keepFrom = buffer.lastIndexOf("<");
                if (keepFrom >= 0 && !finish) {
                    appendContent(result, ChunkType.THINKING, buffer.substring(0, keepFrom));
                    buffer.delete(0, keepFrom);
                    break;
                }

                appendContent(result, ChunkType.THINKING, buffer.toString());
                buffer.setLength(0);
                break;
            }

            OpenMatch open = findOpenTag(buffer);
            if (open != null) {
                appendContent(result, ChunkType.ANSWER, buffer.substring(0, open.index()));
                buffer.delete(0, open.index() + open.tag().length());
                inThinking = true;
                if (!thinkingStarted) {
                    thinkingStarted = true;
                    result.add(new ParsedChunk(ChunkType.THINKING_START, ""));
                }
                continue;
            }

            if (!finish && buffer.length() <= OPEN_TAIL) {
                break;
            }

            int flushLength = finish ? buffer.length() : buffer.length() - OPEN_TAIL;
            appendContent(result, ChunkType.ANSWER, buffer.substring(0, flushLength));
            buffer.delete(0, flushLength);
            break;
        }

        return result;
    }

    private void appendContent(List<ParsedChunk> result, ChunkType type, String content) {
        if (content != null && !content.isEmpty()) {
            result.add(new ParsedChunk(type, content));
        }
    }

    private OpenMatch findOpenTag(StringBuilder text) {
        int think = indexOfIgnoreCase(text, THINK_OPEN);
        int thinking = indexOfIgnoreCase(text, THINKING_OPEN);
        if (think < 0) {
            return thinking < 0 ? null : new OpenMatch(thinking, THINKING_OPEN);
        }
        if (thinking < 0) {
            return new OpenMatch(think, THINK_OPEN);
        }
        return think <= thinking ? new OpenMatch(think, THINK_OPEN) : new OpenMatch(thinking, THINKING_OPEN);
    }

    private CloseMatch findCloseTag(StringBuilder text) {
        int think = indexOfIgnoreCase(text, THINK_CLOSE);
        int thinking = indexOfIgnoreCase(text, THINKING_CLOSE);
        if (think < 0) {
            return thinking < 0 ? null : new CloseMatch(thinking, THINKING_CLOSE);
        }
        if (thinking < 0) {
            return new CloseMatch(think, THINK_CLOSE);
        }
        return think <= thinking ? new CloseMatch(think, THINK_CLOSE) : new CloseMatch(thinking, THINKING_CLOSE);
    }

    private int indexOfIgnoreCase(StringBuilder source, String needle) {
        int max = source.length() - needle.length();
        for (int i = 0; i <= max; i++) {
            int j = 0;
            while (j < needle.length()
                    && Character.toLowerCase(source.charAt(i + j)) == Character.toLowerCase(needle.charAt(j))) {
                j++;
            }
            if (j == needle.length()) {
                return i;
            }
        }
        return -1;
    }

    public enum ChunkType {
        ANSWER,
        THINKING_START,
        THINKING,
        THINKING_DONE
    }

    public record ParsedChunk(ChunkType type, String content) {
    }

    private record OpenMatch(int index, String tag) {
    }

    private record CloseMatch(int index, String tag) {
    }
}
