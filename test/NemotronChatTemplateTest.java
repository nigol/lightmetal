import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import lm.configuration.control.ZCfg;
import lm.configuration.entity.Token;
import lm.inspection.entity.GGUFMetadata;
import lm.prompting.entity.AssistantText;
import lm.prompting.entity.AssistantToolCalls;
import lm.prompting.entity.ToolResult;
import lm.prompting.entity.Turn;
import lm.prompting.entity.UserText;
import lm.prompting.entity.UserToolResults;
import lm.prompting.control.ChatTemplate;
import lm.prompting.control.ModelFamily;
import lm.prompting.control.NemotronChatTemplate;
import lm.tools.control.ToolCallParser;
import lm.tools.entity.Tool;
import lm.tools.entity.ToolCall;

void main() {
    ZCfg.load("lightmetal-tests-no-such-file");

    var tpl = ModelFamily.NEMOTRON.template();
    if (!(tpl instanceof NemotronChatTemplate))
        throw new AssertionError("ModelFamily.NEMOTRON.template() returned " + tpl.getClass());

    testFamilyMatchesNemotronBeforeMistralNemo();
    testPlainChatGenerationPromptShape(tpl);
    testSystemBlockContent(tpl);
    testThinkingPrefill();
    testToolDeclarationFormat(tpl);
    testAssistantHistoryDropsReasoning(tpl);
    testToolCallTurnRendering(tpl);
    testToolResponseRendering(tpl);
    testParserStripsThinkingAndEndMarker(tpl);
    testParserRecoversToolCalls(tpl);
    testStopSequences(tpl);
    testChannelTaggingAcrossTokenBoundaries();

    IO.println("[ok] nemotron template render + parse");
}

// "Nemotron..." also contains the "nemo" fragment — the more specific entry must win
void testFamilyMatchesNemotronBeforeMistralNemo() {
    var metadata = new GGUFMetadata(3, 0L, Map.of("general.name", "Nemotron-3-Nano-30B-A3B"));
    var family = ModelFamily.from(metadata).orElseThrow(
            () -> new AssertionError("no family matched Nemotron-3-Nano-30B-A3B"));
    if (family != ModelFamily.NEMOTRON)
        throw new AssertionError("expected NEMOTRON, got " + family);
}

void testPlainChatGenerationPromptShape(ChatTemplate tpl) {
    var out = tpl.render("", List.of(), List.of(new UserText("hi")));

    // system header is always emitted (part of the trained format), thinking disabled by default
    var expected = "<|im_start|>system\n<|im_end|>\n"
            + "<|im_start|>user\nhi<|im_end|>\n"
            + "<|im_start|>assistant\n<think></think>";
    if (!expected.equals(out))
        throw new AssertionError("plain chat render mismatch.\nexpected:\n" + expected + "\nactual:\n" + out);
}

void testSystemBlockContent(ChatTemplate tpl) {
    var out = tpl.render("be terse", List.of(), List.of(new UserText("hi")));
    if (!out.startsWith("<|im_start|>system\nbe terse<|im_end|>\n"))
        throw new AssertionError("expected system block at top, got:\n" + out);
}

void testThinkingPrefill() {
    System.setProperty("nemotron.enable_thinking", "true");
    ZCfg.load("lightmetal-tests-no-such-file");
    try {
        var out = new NemotronChatTemplate().render("", List.of(), List.of(new UserText("hi")));
        if (!out.endsWith("<|im_start|>assistant\n<think>\n"))
            throw new AssertionError("expected open think prefill, got:\n" + out);
    } finally {
        System.clearProperty("nemotron.enable_thinking");
        ZCfg.load("lightmetal-tests-no-such-file");
    }
}

void testToolDeclarationFormat(ChatTemplate tpl) {
    var schema = new JSONObject()
            .put("type", "object")
            .put("properties", new JSONObject()
                    .put("location", new JSONObject()
                            .put("type", "string")
                            .put("description", "City name")))
            .put("required", new JSONArray(List.of("location")));
    var tool = new Tool("get_weather", "Get current weather", schema);

    var out = tpl.render("", List.of(tool), List.of(new UserText("hi")));

    var expectedDecl = "<function>\n<name>get_weather</name>\n"
            + "<description>Get current weather</description>\n"
            + "<parameters>\n"
            + "<parameter>\n<name>location</name>\n<type>string</type>\n<description>City name</description>\n</parameter>\n"
            + "<required>[\"location\"]</required>\n"
            + "</parameters>\n</function>";
    if (!out.contains(expectedDecl))
        throw new AssertionError("tool declaration not found.\nexpected substring:\n" + expectedDecl + "\nactual:\n" + out);
    if (!out.contains("# Tools\n\nYou have access to the following functions:"))
        throw new AssertionError("tools preamble missing:\n" + out);
    if (!out.contains("If you choose to call a function ONLY reply in the following format with NO suffix:"))
        throw new AssertionError("tool instructions missing:\n" + out);
}

void testAssistantHistoryDropsReasoning(ChatTemplate tpl) {
    var turns = List.<Turn>of(
            new UserText("hi"),
            new AssistantText("secret reasoning</think>\n\nhello!"),
            new UserText("again"));
    var out = tpl.render("", List.of(), turns);

    if (!out.contains("<|im_start|>assistant\n<think></think>hello!<|im_end|>\n"))
        throw new AssertionError("expected truncated assistant history, got:\n" + out);
    if (out.contains("secret reasoning"))
        throw new AssertionError("reasoning leaked into history:\n" + out);
}

void testToolCallTurnRendering(ChatTemplate tpl) {
    var call = new ToolCall("toolu_1", "get_weather", new JSONObject().put("location", "Paris"));
    var turns = List.<Turn>of(
            new UserText("weather in Paris?"),
            new AssistantToolCalls("", List.of(call)),
            new UserToolResults(List.of(new ToolResult("toolu_1", "22C, sunny"))));
    var out = tpl.render("", List.of(), turns);

    var expectedCall = "<|im_start|>assistant\n<think></think>"
            + "<tool_call>\n<function=get_weather>\n"
            + "<parameter=location>\nParis\n</parameter>\n"
            + "</function>\n</tool_call>\n<|im_end|>\n";
    if (!out.contains(expectedCall))
        throw new AssertionError("tool call turn mismatch.\nexpected substring:\n" + expectedCall + "\nactual:\n" + out);
}

void testToolResponseRendering(ChatTemplate tpl) {
    var turns = List.<Turn>of(
            new UserText("weather?"),
            new AssistantToolCalls("", List.of(new ToolCall("toolu_1", "get_weather", new JSONObject()))),
            new UserToolResults(List.of(new ToolResult("toolu_1", "22C, sunny"))));
    var out = tpl.render("", List.of(), turns);

    if (!out.contains("<|im_start|>user\n<tool_response>\n22C, sunny\n</tool_response>\n<|im_end|>\n"))
        throw new AssertionError("tool response turn mismatch:\n" + out);
}

void testParserStripsThinkingAndEndMarker(ChatTemplate tpl) {
    var parsed = tpl.parse("internal reasoning\n</think>\n\nThe answer is 42.<|im_end|>");
    if (!(parsed instanceof ToolCallParser.Text t))
        throw new AssertionError("expected Text, got " + parsed);
    if (!"The answer is 42.".equals(t.text()))
        throw new AssertionError("thinking not stripped: '" + t.text() + "'");
}

void testParserRecoversToolCalls(ChatTemplate tpl) {
    var raw = "reasoning</think>Checking the weather.\n"
            + "<tool_call>\n<function=get_weather>\n"
            + "<parameter=location>\nParis\n</parameter>\n"
            + "<parameter=note>\nline one\nline two\n</parameter>\n"
            + "<parameter=options>\n{\"units\": \"celsius\"}\n</parameter>\n"
            + "</function>\n</tool_call>";
    var parsed = tpl.parse(raw);
    if (!(parsed instanceof ToolCallParser.Calls c))
        throw new AssertionError("expected Calls, got " + parsed);
    if (!"Checking the weather.".equals(c.leadingText()))
        throw new AssertionError("leading text wrong: '" + c.leadingText() + "'");
    if (c.calls().size() != 1)
        throw new AssertionError("expected 1 call, got " + c.calls().size());
    var only = c.calls().getFirst();
    if (!"get_weather".equals(only.name()))
        throw new AssertionError("call name wrong: " + only.name());
    if (!"Paris".equals(only.input().optString("location")))
        throw new AssertionError("location arg wrong: " + only.input());
    if (!"line one\nline two".equals(only.input().optString("note")))
        throw new AssertionError("multi-line arg wrong: " + only.input());
    var options = only.input().optJSONObject("options");
    if (options == null || !"celsius".equals(options.optString("units")))
        throw new AssertionError("JSON arg not parsed: " + only.input());
}

void testStopSequences(ChatTemplate tpl) {
    if (!tpl.stopSequences().contains("<|im_end|>"))
        throw new AssertionError("expected <|im_end|> stop sequence, got " + tpl.stopSequences());
}

void testChannelTaggingAcrossTokenBoundaries() {
    System.setProperty("nemotron.enable_thinking", "true");
    ZCfg.load("lightmetal-tests-no-such-file");
    try {
        var tpl = new NemotronChatTemplate();
        var pieces = List.of("rea", "soning</th", "ink>ans", "wer");
        var raw = pieces.stream().map(p -> new Token(0, p));

        var thought = new StringBuilder();
        var answer = new StringBuilder();
        tpl.tagChannels(raw).forEach(t -> {
            if ("thought".equals(t.channel())) thought.append(t.text());
            else answer.append(t.text());
        });

        if (!"reasoning".contentEquals(thought))
            throw new AssertionError("thought channel wrong: '" + thought + "'");
        if (!"answer".contentEquals(answer))
            throw new AssertionError("final channel wrong: '" + answer + "'");
    } finally {
        System.clearProperty("nemotron.enable_thinking");
        ZCfg.load("lightmetal-tests-no-such-file");
    }
}
