package lm.prompting.control;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.json.JSONArray;
import org.json.JSONObject;

import lm.configuration.control.ZCfg;
import lm.http.entity.AnthropicMessagesRequest.AssistantText;
import lm.http.entity.AnthropicMessagesRequest.AssistantToolCalls;
import lm.http.entity.AnthropicMessagesRequest.Turn;
import lm.http.entity.AnthropicMessagesRequest.UserText;
import lm.http.entity.AnthropicMessagesRequest.UserToolResults;
import lm.tools.entity.Tool;
import lm.tools.entity.ToolCall;

public interface PromptTemplate {

    static String modelSettings() {
        var effort = ZCfg.string("mistral4.reasoning_effort", "none");
        return "[MODEL_SETTINGS]{\"reasoning_effort\": \"" + effort + "\"}[/MODEL_SETTINGS]";
    }

    static String mistralInstruct(String userPrompt) {
        return "[INST] " + userPrompt + " [/INST]";
    }

    static String mistralChat(String system, List<String> alternating) {
        if (alternating == null || alternating.isEmpty()) {
            return mistralInstruct("");
        }
        var sys = (system == null || system.isBlank()) ? "" : system.strip() + "\n\n";
        var sb = new StringBuilder();
        sb.append("[INST] ").append(sys).append(alternating.get(0)).append(" [/INST]");
        for (var i = 1; i < alternating.size(); i++) {
            var turn = alternating.get(i);
            if (i % 2 == 1) {
                sb.append(' ').append(turn).append("</s>");
            } else {
                sb.append("[INST] ").append(turn).append(" [/INST]");
            }
        }
        return sb.toString();
    }

    static String mistral4(String system, List<Tool> tools, List<Turn> turns) {
        var sb = new StringBuilder();
        if (system != null && !system.isEmpty()) {
            sb.append("[SYSTEM_PROMPT]").append(system).append("[/SYSTEM_PROMPT]");
        }
        if (!tools.isEmpty()) {
            sb.append("[AVAILABLE_TOOLS]").append(mistralToolsJson(tools)).append("[/AVAILABLE_TOOLS]");
        }
        sb.append(modelSettings());
        for (var turn : turns) {
            appendTurn(sb, turn);
        }
        return sb.toString();
    }

    private static void appendTurn(StringBuilder sb, Turn turn) {
        switch (turn) {
            case UserText u -> sb.append("[INST]").append(u.text()).append("[/INST]");
            case AssistantText a -> sb.append(a.text()).append("</s>");
            case AssistantToolCalls a -> {
                if (!a.text().isBlank()) sb.append(a.text());
                for (var call : a.calls()) {
                    sb.append("[TOOL_CALLS]").append(call.name())
                            .append("[ARGS]").append(pythonJson(call.input()));
                }
                sb.append("</s>");
            }
            case UserToolResults r -> {
                for (var result : r.results()) {
                    sb.append("[TOOL_RESULTS]").append(result.content()).append("[/TOOL_RESULTS]");
                }
            }
        }
    }

    private static String mistralToolsJson(List<Tool> tools) {
        var arr = new JSONArray();
        for (var t : tools) {
            arr.put(new JSONObject()
                    .put("type", "function")
                    .put("function", new JSONObject()
                            .put("name", t.name())
                            .put("description", t.description())
                            .put("parameters", t.inputSchema() == null ? new JSONObject() : t.inputSchema())));
        }
        return pythonJson(arr);
    }

    /**
     * Convert org.json's compact output to Python json.dumps default formatting
     * (", " and ": " separators) — matches what Jinja's tojson produces.
     */
    private static String pythonJson(Object jsonValue) {
        return reformat(jsonValue.toString());
    }

    private static String reformat(String compact) {
        var sb = new StringBuilder(compact.length() + 32);
        var inString = false;
        var escape = false;
        for (var i = 0; i < compact.length(); i++) {
            var c = compact.charAt(i);
            sb.append(c);
            if (escape) { escape = false; continue; }
            if (c == '\\') { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString && (c == ':' || c == ',')) sb.append(' ');
        }
        return sb.toString();
    }

    String GEMMA_QUOTE = "<|\"|>";
    String GEMMA_TURN_OPEN = "<|turn>";
    String GEMMA_TURN_CLOSE = "<turn|>";
    String GEMMA_TOOL_OPEN = "<|tool>";
    String GEMMA_TOOL_CLOSE = "<tool|>";
    String GEMMA_TOOL_CALL_OPEN = "<|tool_call>";
    String GEMMA_TOOL_CALL_CLOSE = "<tool_call|>";
    String GEMMA_TOOL_RESPONSE_OPEN = "<|tool_response>";
    String GEMMA_TOOL_RESPONSE_CLOSE = "<tool_response|>";
    String GEMMA_CHANNEL_OPEN = "<|channel>";
    String GEMMA_CHANNEL_CLOSE = "<channel|>";
    String GEMMA_THINK = "<|think|>";
    Set<String> GEMMA_PROPERTY_STANDARD_KEYS =
            Set.of("description", "type", "properties", "required", "nullable");

    static String gemma4(String system, List<Tool> tools, List<Turn> turns) {
        var enableThinking = ZCfg.bool("gemma4.enable_thinking", false);
        var sb = new StringBuilder();
        var hasHeader = enableThinking
                || (tools != null && !tools.isEmpty())
                || (system != null && !system.isBlank());
        if (hasHeader) {
            sb.append(GEMMA_TURN_OPEN).append("system\n");
            if (enableThinking) sb.append(GEMMA_THINK).append('\n');
            if (system != null && !system.isBlank()) sb.append(system.strip());
            if (tools != null) {
                for (var t : tools) {
                    sb.append(GEMMA_TOOL_OPEN);
                    appendGemmaToolDeclaration(sb, t);
                    sb.append(GEMMA_TOOL_CLOSE);
                }
            }
            sb.append(GEMMA_TURN_CLOSE).append('\n');
        }

        var prevType = "";
        var prevNonToolRole = "";
        var i = 0;
        while (i < turns.size()) {
            var turn = turns.get(i);

            if (turn instanceof UserToolResults r) {
                for (var result : r.results()) {
                    appendGemmaToolResponse(sb, "unknown", result.content());
                }
                prevType = "tool_response";
                i++;
                continue;
            }

            var role = (turn instanceof AssistantText || turn instanceof AssistantToolCalls)
                    ? "model" : "user";
            var continueModel = "model".equals(role) && "model".equals(prevNonToolRole);
            if (!continueModel) {
                sb.append(GEMMA_TURN_OPEN).append(role).append('\n');
            }

            var hasContent = false;
            var inlinedResponses = false;

            switch (turn) {
                case AssistantToolCalls a -> {
                    for (var call : a.calls()) {
                        sb.append(GEMMA_TOOL_CALL_OPEN)
                                .append("call:").append(call.name()).append('{');
                        appendGemmaArgsBody(sb, call.input());
                        sb.append('}').append(GEMMA_TOOL_CALL_CLOSE);
                    }
                    prevType = "tool_call";

                    var k = i + 1;
                    while (k < turns.size() && turns.get(k) instanceof UserToolResults r) {
                        for (var result : r.results()) {
                            appendGemmaToolResponse(sb,
                                    toolNameForCallId(a.calls(), result.callId()),
                                    result.content());
                        }
                        inlinedResponses = true;
                        prevType = "tool_response";
                        k++;
                    }
                    i = k - 1;

                    var text = gemmaStripThinking(a.text());
                    if (!text.isBlank()) {
                        sb.append(text);
                        hasContent = true;
                    }
                }
                case AssistantText a -> {
                    var text = gemmaStripThinking(a.text());
                    if (!text.isBlank()) {
                        sb.append(text);
                        hasContent = true;
                    }
                    prevType = "";
                }
                case UserText u -> {
                    var text = u.text() == null ? "" : u.text().strip();
                    if (!text.isEmpty()) {
                        sb.append(text);
                        hasContent = true;
                    }
                    prevType = "";
                }
                case UserToolResults ignored -> { /* handled above */ }
            }

            if ("tool_call".equals(prevType) && !inlinedResponses) {
                sb.append(GEMMA_TOOL_RESPONSE_OPEN);
            } else if (!(inlinedResponses && !hasContent)) {
                sb.append(GEMMA_TURN_CLOSE).append('\n');
            }

            prevNonToolRole = role;
            i++;
        }

        if (!"tool_response".equals(prevType) && !"tool_call".equals(prevType)) {
            sb.append(GEMMA_TURN_OPEN).append("model\n");
            if (!enableThinking) {
                sb.append(GEMMA_CHANNEL_OPEN).append("thought\n").append(GEMMA_CHANNEL_CLOSE);
            }
        }
        return sb.toString();
    }

    static String gemmaStripThinking(String text) {
        if (text == null || text.isEmpty()) return "";
        var sb = new StringBuilder();
        var parts = text.split(java.util.regex.Pattern.quote(GEMMA_CHANNEL_CLOSE), -1);
        for (var part : parts) {
            var open = part.indexOf(GEMMA_CHANNEL_OPEN);
            sb.append(open >= 0 ? part.substring(0, open) : part);
        }
        return sb.toString().strip();
    }

    private static String toolNameForCallId(List<ToolCall> calls, String callId) {
        if (callId == null) return "unknown";
        for (var c : calls) {
            if (callId.equals(c.id())) return c.name();
        }
        return "unknown";
    }

    private static void appendGemmaToolResponse(StringBuilder sb, String toolName, String content) {
        sb.append(GEMMA_TOOL_RESPONSE_OPEN)
                .append("response:").append(toolName).append("{value:")
                .append(gemmaQuoted(content == null ? "" : content))
                .append('}').append(GEMMA_TOOL_RESPONSE_CLOSE);
    }

    private static String gemmaQuoted(String s) {
        return GEMMA_QUOTE + s + GEMMA_QUOTE;
    }

    private static void appendGemmaArgsBody(StringBuilder sb, JSONObject input) {
        if (input == null) return;
        var sorted = new TreeSet<>(input.keySet());
        var first = true;
        for (var key : sorted) {
            if (!first) sb.append(',');
            first = false;
            sb.append(key).append(':');
            appendGemmaValue(sb, input.get(key));
        }
    }

    private static void appendGemmaValue(StringBuilder sb, Object value) {
        switch (value) {
            case null -> sb.append("null");
            case String s -> sb.append(gemmaQuoted(s));
            case Boolean b -> sb.append(b);
            case Number n -> sb.append(n);
            case JSONObject obj -> {
                sb.append('{');
                appendGemmaArgsBody(sb, obj);
                sb.append('}');
            }
            case JSONArray arr -> {
                sb.append('[');
                for (var i = 0; i < arr.length(); i++) {
                    if (i > 0) sb.append(',');
                    appendGemmaValue(sb, arr.get(i));
                }
                sb.append(']');
            }
            default -> sb.append(gemmaQuoted(value.toString()));
        }
    }

    private static void appendGemmaToolDeclaration(StringBuilder sb, Tool tool) {
        sb.append("declaration:").append(tool.name()).append('{')
                .append("description:").append(gemmaQuoted(tool.description() == null ? "" : tool.description()));
        var params = tool.inputSchema();
        if (params != null) {
            sb.append(",parameters:{");
            var hasInner = false;
            var props = params.optJSONObject("properties");
            if (props != null && !props.isEmpty()) {
                sb.append("properties:{");
                appendGemmaProperties(sb, props, false);
                sb.append("},");
                hasInner = true;
            }
            var required = params.optJSONArray("required");
            if (required != null && !required.isEmpty()) {
                sb.append("required:[");
                for (var i = 0; i < required.length(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(gemmaQuoted(required.optString(i, "")));
                }
                sb.append("],");
                hasInner = true;
            }
            if (hasInner || params.has("type")) {
                sb.append("type:")
                        .append(gemmaQuoted(params.optString("type", "object").toUpperCase()))
                        .append('}');
            } else {
                sb.append('}');
            }
        }
        sb.append('}');
    }

    private static void appendGemmaProperties(StringBuilder sb, JSONObject props, boolean filterStdKeys) {
        var first = true;
        for (var key : new TreeSet<>(props.keySet())) {
            if (filterStdKeys && GEMMA_PROPERTY_STANDARD_KEYS.contains(key)) continue;
            var value = props.optJSONObject(key);
            if (value == null) continue;
            if (!first) sb.append(',');
            first = false;
            sb.append(key).append(":{");
            appendGemmaPropertyContents(sb, value);
            sb.append('}');
        }
    }

    private static void appendGemmaPropertyContents(StringBuilder sb, JSONObject value) {
        var addComma = false;
        var description = value.optString("description", null);
        if (description != null && !description.isEmpty()) {
            sb.append("description:").append(gemmaQuoted(description));
            addComma = true;
        }
        var type = value.optString("type", "").toUpperCase();
        if ("STRING".equals(type)) {
            var enums = value.optJSONArray("enum");
            if (enums != null && !enums.isEmpty()) {
                if (addComma) sb.append(',');
                addComma = true;
                sb.append("enum:[");
                for (var i = 0; i < enums.length(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(gemmaQuoted(enums.optString(i, "")));
                }
                sb.append(']');
            }
        } else if ("ARRAY".equals(type)) {
            var items = value.optJSONObject("items");
            if (items != null && !items.isEmpty()) {
                if (addComma) sb.append(',');
                addComma = true;
                sb.append("items:{");
                appendGemmaItemContents(sb, items);
                sb.append('}');
            }
        }
        if (value.optBoolean("nullable", false)) {
            if (addComma) sb.append(',');
            addComma = true;
            sb.append("nullable:true");
        }
        if ("OBJECT".equals(type)) {
            var nested = value.optJSONObject("properties");
            if (nested != null) {
                if (addComma) sb.append(',');
                addComma = true;
                sb.append("properties:{");
                appendGemmaProperties(sb, nested, false);
                sb.append('}');
            } else {
                if (addComma) sb.append(',');
                addComma = true;
                sb.append("properties:{");
                appendGemmaProperties(sb, value, true);
                sb.append('}');
            }
            var nestedReq = value.optJSONArray("required");
            if (nestedReq != null && !nestedReq.isEmpty()) {
                if (addComma) sb.append(',');
                addComma = true;
                sb.append("required:[");
                for (var i = 0; i < nestedReq.length(); i++) {
                    if (i > 0) sb.append(',');
                    sb.append(gemmaQuoted(nestedReq.optString(i, "")));
                }
                sb.append(']');
            }
        }
        if (addComma) sb.append(',');
        sb.append("type:").append(gemmaQuoted(type));
    }

    private static void appendGemmaItemContents(StringBuilder sb, JSONObject items) {
        var first = true;
        for (var key : new TreeSet<>(items.keySet())) {
            var raw = items.opt(key);
            if (raw == null) continue;
            if (!first) sb.append(',');
            first = false;
            switch (key) {
                case "properties" -> {
                    sb.append("properties:{");
                    if (raw instanceof JSONObject obj) {
                        appendGemmaProperties(sb, obj, false);
                    }
                    sb.append('}');
                }
                case "required" -> {
                    sb.append("required:[");
                    if (raw instanceof JSONArray arr) {
                        for (var i = 0; i < arr.length(); i++) {
                            if (i > 0) sb.append(',');
                            sb.append(gemmaQuoted(arr.optString(i, "")));
                        }
                    }
                    sb.append(']');
                }
                case "type" -> {
                    if (raw instanceof String s) {
                        sb.append("type:").append(gemmaQuoted(s.toUpperCase()));
                    } else if (raw instanceof JSONArray arr) {
                        sb.append("type:[");
                        for (var i = 0; i < arr.length(); i++) {
                            if (i > 0) sb.append(',');
                            sb.append(gemmaQuoted(arr.optString(i, "").toUpperCase()));
                        }
                        sb.append(']');
                    }
                }
                default -> {
                    sb.append(key).append(':');
                    appendGemmaValue(sb, raw);
                }
            }
        }
    }
}
