package lm.generation.entity;

import java.util.List;

import lm.tools.control.ToolCallParser;
import lm.tools.entity.ToolCall;

public record Response(
        String text,
        List<ToolCall> calls,
        StopReason reason,
        int inputTokens,
        int outputTokens) {

    public enum StopReason { END_TURN, MAX_TOKENS, TOOL_USE }

    public boolean hasToolCalls() {
        return !calls.isEmpty();
    }

    public static Response from(ToolCallParser.Parsed parsed, long emitted, int maxTokens, int inputTokens) {
        return switch (parsed) {
            case ToolCallParser.Calls c -> new Response(
                    c.leadingText(), List.copyOf(c.calls()),
                    StopReason.TOOL_USE, inputTokens, (int) emitted);
            case ToolCallParser.Text t -> new Response(
                    t.text(), List.of(),
                    emitted >= maxTokens ? StopReason.MAX_TOKENS : StopReason.END_TURN,
                    inputTokens, (int) emitted);
        };
    }
}
