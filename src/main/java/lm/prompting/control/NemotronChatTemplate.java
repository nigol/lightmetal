package lm.prompting.control;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Gatherer;
import java.util.stream.Stream;

import org.json.JSONObject;
import org.json.JSONTokener;

import lm.configuration.control.ZCfg;
import lm.configuration.entity.Token;
import lm.prompting.entity.Turn;
import lm.tools.control.ToolCallParser;
import lm.tools.entity.Tool;
import lm.tools.entity.ToolCall;

public final class NemotronChatTemplate implements ChatTemplate {

    private final boolean enableThinking = ZCfg.bool("nemotron.enable_thinking", false);

    private static final String FUNCTION_OPEN = "<function=";
    private static final String FUNCTION_CLOSE = "</function>";
    private static final String PARAMETER_OPEN = "<parameter=";
    private static final String PARAMETER_CLOSE = "</parameter>";

    @Override
    public String render(String system, List<Tool> tools, List<Turn> turns) {
        return PromptTemplate.nemotron(system, tools, turns);
    }

    // <|im_end|> is the EOG token in the Nemotron GGUF (id 11, verified against
    // Nemotron-3-Nano-30B-A3B) — this is a safety net for quantizations that
    // miss the mapping, like GemmaChatTemplate's <turn|> stop.
    @Override
    public List<String> stopSequences() {
        return List.of(PromptTemplate.NEMOTRON_IM_END);
    }

    @Override
    public List<String> toolCallOpenMarkers() {
        return List.of(PromptTemplate.NEMOTRON_TOOL_CALL_OPEN);
    }

    // The generation prompt prefills `<think>\n`, so the model starts inside its
    // reasoning — streamed tokens are "thought" until `</think>` arrives.
    @Override
    public Stream<Token> tagChannels(Stream<Token> tokens) {
        return tokens.gather(Gatherer.<Token, ChannelFilter, Token>ofSequential(
                () -> new ChannelFilter(PromptTemplate.NEMOTRON_THINK_CLOSE, enableThinking),
                (filter, in, down) -> {
                    filter.consume(in).forEach(down::push);
                    return true;
                },
                (filter, down) -> filter.flush().forEach(down::push)));
    }

    @Override
    public ToolCallParser.Parsed parse(String generated) {
        var text = stripThinking(stripTrailingEnd(generated == null ? "" : generated));
        var firstOpen = text.indexOf(PromptTemplate.NEMOTRON_TOOL_CALL_OPEN);
        if (firstOpen < 0) {
            return new ToolCallParser.Text(text.strip());
        }
        var calls = extractCalls(text, firstOpen);
        return calls.isEmpty()
                ? new ToolCallParser.Text(text.strip())
                : new ToolCallParser.Calls(text.substring(0, firstOpen).strip(), calls);
    }

    // The opening <think> is part of the PROMPT prefill, so raw output usually
    // looks like `reasoning</think>answer` — keep what follows the last close.
    static String stripThinking(String text) {
        var close = text.lastIndexOf(PromptTemplate.NEMOTRON_THINK_CLOSE);
        if (close >= 0) {
            return text.substring(close + PromptTemplate.NEMOTRON_THINK_CLOSE.length());
        }
        var open = text.indexOf(PromptTemplate.NEMOTRON_THINK_OPEN);
        return open >= 0 ? text.substring(0, open) : text;
    }

    static String stripTrailingEnd(String s) {
        var t = s.stripTrailing();
        while (t.endsWith(PromptTemplate.NEMOTRON_IM_END)) {
            t = t.substring(0, t.length() - PromptTemplate.NEMOTRON_IM_END.length()).stripTrailing();
        }
        return t;
    }

    private static List<ToolCall> extractCalls(String text, int from) {
        var out = new ArrayList<ToolCall>();
        var cursor = from;
        while (true) {
            var open = text.indexOf(PromptTemplate.NEMOTRON_TOOL_CALL_OPEN, cursor);
            if (open < 0) break;
            var close = text.indexOf(PromptTemplate.NEMOTRON_TOOL_CALL_CLOSE, open);
            if (close < 0) break;
            var body = text.substring(open + PromptTemplate.NEMOTRON_TOOL_CALL_OPEN.length(), close);
            var call = parseCall(body);
            if (call != null) out.add(call);
            cursor = close + PromptTemplate.NEMOTRON_TOOL_CALL_CLOSE.length();
        }
        return out;
    }

    private static ToolCall parseCall(String body) {
        var open = body.indexOf(FUNCTION_OPEN);
        if (open < 0) return null;
        var nameEnd = body.indexOf('>', open);
        if (nameEnd < 0) return null;
        var name = body.substring(open + FUNCTION_OPEN.length(), nameEnd).strip();
        if (name.isEmpty()) return null;
        var end = body.indexOf(FUNCTION_CLOSE, nameEnd);
        var parameters = body.substring(nameEnd + 1, end < 0 ? body.length() : end);
        return ToolCall.of(name, parseParameters(parameters));
    }

    private static JSONObject parseParameters(String region) {
        var args = new JSONObject();
        var cursor = 0;
        while (true) {
            var open = region.indexOf(PARAMETER_OPEN, cursor);
            if (open < 0) break;
            var nameEnd = region.indexOf('>', open);
            if (nameEnd < 0) break;
            var key = region.substring(open + PARAMETER_OPEN.length(), nameEnd);
            var close = region.indexOf(PARAMETER_CLOSE, nameEnd);
            if (close < 0) break;
            var raw = trimDelimiters(region.substring(nameEnd + 1, close));
            args.put(key, parameterValue(raw));
            cursor = close + PARAMETER_CLOSE.length();
        }
        return args;
    }

    // The trained format wraps each value in exactly one newline on each side;
    // inner newlines belong to multi-line values and must survive.
    static String trimDelimiters(String raw) {
        var s = raw.startsWith("\n") ? raw.substring(1) : raw;
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static Object parameterValue(String value) {
        var stripped = value.strip();
        if (stripped.startsWith("{") || stripped.startsWith("[")) {
            try {
                return new JSONTokener(stripped).nextValue();
            } catch (IllegalArgumentException | IllegalStateException e) {
                return value;
            }
        }
        return value;
    }
}
