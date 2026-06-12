package lm.http.control;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import lm.configuration.control.ZCfg;
import lm.configuration.entity.GenerationConfig;
import lm.generation.boundary.LightMetal;
import lm.generation.control.TokenAccumulator;
import lm.generation.entity.Response;
import lm.http.entity.AnthropicMessagesRequest;
import lm.prompting.entity.AssistantText;
import lm.prompting.entity.UserText;
import lm.http.entity.OpenAIChatRequest;
import lm.logging.control.Log;
import lm.prompting.control.PromptTemplate;

public final class ChatCompletionsHandler implements HttpHandler {

    private final LightMetal lm;
    private final String legacyOverride;

    public ChatCompletionsHandler(LightMetal lm) {
        this.lm = lm;
        var override = ZCfg.string("template", "");
        this.legacyOverride = isLegacyBasic(override) ? override : "";
        Log.system("[template=" + (legacyOverride.isEmpty()
                ? lm.template().getClass().getSimpleName() : legacyOverride) + "]");
    }

    private static boolean isLegacyBasic(String override) {
        return "v0.3".equals(override) || "v3".equals(override) || "basic".equals(override);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        var startNanos = System.nanoTime();
        var method = exchange.getRequestMethod();
        var uri = exchange.getRequestURI();
        Log.http("--> " + method + " " + uri + " from " + exchange.getRemoteAddress());
        var status = 0;
        try (exchange) {
            if (!"POST".equalsIgnoreCase(method)) {
                status = 405;
                writeError(exchange, status, "method_not_allowed", "use POST");
                return;
            }
            String body;
            try (InputStream in = exchange.getRequestBody()) {
                body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            JSONObject root;
            try {
                root = new JSONObject(new JSONTokener(body));
            } catch (IllegalArgumentException | IllegalStateException e) {
                status = 400;
                writeError(exchange, status, "invalid_request_error", "malformed JSON: " + e.getMessage());
                return;
            }
            if (root.optBoolean("stream", false)) {
                status = 400;
                writeError(exchange, status, "invalid_request_error",
                        "streaming is not supported yet; set stream:false or omit it");
                return;
            }
            OpenAIChatRequest req;
            try {
                req = OpenAIChatRequest.from(root, GenerationConfig.defaults());
            } catch (IllegalArgumentException e) {
                status = 400;
                writeError(exchange, status, "invalid_request_error", e.getMessage());
                return;
            }
            try {
                writeOk(exchange, generate(req));
                status = 200;
            } catch (RuntimeException e) {
                status = 500;
                Log.error("request failed", e);
                writeError(exchange, status, "api_error", e.getMessage());
            }
        } finally {
            var ms = (System.nanoTime() - startNanos) / 1_000_000;
            Log.http("<-- " + method + " " + uri + " " + status + " (" + ms + " ms)");
        }
    }

    JSONObject generate(OpenAIChatRequest req) {
        var msgReq = req.toAnthropicMessagesRequest();
        var cfg = baseConfig(req);
        var response = legacyOverride.isEmpty()
                ? lm.chat(msgReq.system(), msgReq.tools(), msgReq.turns(), cfg)
                : generateLegacy(msgReq, cfg);
        return toOpenAIJson(response);
    }

    Response generateLegacy(AnthropicMessagesRequest req, GenerationConfig cfg) {
        var prompt = PromptTemplate.mistralChat(req.system(), flattenForBasic(req));
        Log.debug("[prompt template=" + legacyOverride + "]\n" + prompt + "\n[/prompt]");
        var acc = new TokenAccumulator();
        synchronized (lm) {
            lm.reset();
            try (var stream = lm.complete(prompt, cfg)) {
                stream.forEach(acc);
            }
        }
        var reason = acc.count() >= cfg.maxTokens()
                ? Response.StopReason.MAX_TOKENS : Response.StopReason.END_TURN;
        return new Response(acc.text(), List.of(), reason,
                estimateTokens(prompt), (int) acc.count());
    }

    JSONObject toOpenAIJson(Response resp) {
        var message = new JSONObject().put("role", "assistant");
        String finishReason;
        if (resp.hasToolCalls()) {
            var leading = resp.text();
            message.put("content", leading.isBlank() ? JSONObject.NULL : leading);
            var toolCalls = new JSONArray();
            for (var call : resp.calls()) {
                toolCalls.put(new JSONObject()
                        .put("id", call.id())
                        .put("type", "function")
                        .put("function", new JSONObject()
                                .put("name", call.name())
                                .put("arguments", call.input().toString())));
            }
            message.put("tool_calls", toolCalls);
            finishReason = "tool_calls";
        } else {
            message.put("content", resp.text());
            finishReason = switch (resp.reason()) {
                case MAX_TOKENS -> "length";
                case END_TURN, TOOL_USE -> "stop";
            };
        }

        var choice = new JSONObject()
                .put("index", 0)
                .put("message", message)
                .put("finish_reason", finishReason);

        return new JSONObject()
                .put("id", "chatcmpl-" + System.nanoTime())
                .put("object", "chat.completion")
                .put("created", System.currentTimeMillis() / 1000L)
                .put("model", lm.metadata().name().orElse("lightmetal"))
                .put("choices", new JSONArray().put(choice))
                .put("usage", new JSONObject()
                        .put("prompt_tokens", resp.inputTokens())
                        .put("completion_tokens", resp.outputTokens())
                        .put("total_tokens", resp.inputTokens() + resp.outputTokens()));
    }

    static List<String> flattenForBasic(AnthropicMessagesRequest req) {
        var out = new ArrayList<String>(req.turns().size());
        for (var turn : req.turns()) {
            switch (turn) {
                case UserText u -> out.add(u.text());
                case AssistantText a -> out.add(a.text());
                default -> { /* skip tool turns: v0.3 has no tool support */ }
            }
        }
        return out;
    }

    static GenerationConfig baseConfig(OpenAIChatRequest req) {
        var d = GenerationConfig.defaults();
        return new GenerationConfig(
                req.maxTokens(),
                req.temperature(),
                d.topP(),
                d.topK(),
                d.minP(),
                System.nanoTime());
    }

    static int estimateTokens(String prompt) {
        if (prompt == null || prompt.isEmpty()) return 0;
        return Math.max(1, prompt.length() / 4);
    }

    static void writeOk(HttpExchange exchange, JSONObject body) throws IOException {
        write(exchange, 200, body.toString());
    }

    static void writeError(HttpExchange exchange, int status, String type, String message) throws IOException {
        var body = new JSONObject()
                .put("error", new JSONObject()
                        .put("type", type)
                        .put("message", message)
                        .put("code", JSONObject.NULL)
                        .put("param", JSONObject.NULL))
                .toString();
        write(exchange, status, body);
    }

    static void write(HttpExchange exchange, int status, String body) throws IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("content-type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
