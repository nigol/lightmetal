package lm.generation.boundary;

import module java.base;

import org.json.JSONObject;
import org.json.JSONTokener;

import lm.configuration.control.ZCfg;
import lm.configuration.entity.GenerationConfig;
import lm.http.control.MessagesHandler;
import lm.http.entity.AnthropicMessagesRequest;
import lm.logging.control.Log;

public final class LightMetalChatProvider implements UnaryOperator<String> {

    @Override
    public String apply(String requestJson) {
        ZCfg.load("lightmetal");
        var root = new JSONObject(new JSONTokener(requestJson));
        var modelPath = Path.of(root.getString("model"));
        Log.system("[model loaded from %s]".formatted(modelPath));
        var req = AnthropicMessagesRequest.from(root, GenerationConfig.fromProperties());
        try (var lm = LightMetal.load(modelPath)) {
            var resp = lm.chat(req.system(), req.tools(), req.turns(), config(req));
            return MessagesHandler.toAnthropicJson(resp,
                    lm.metadata().name().orElse("lightmetal")).toString();
        }
    }

    static GenerationConfig config(AnthropicMessagesRequest req) {
        var d = GenerationConfig.defaults();
        return new GenerationConfig(
                req.maxTokens(),
                req.temperature(),
                d.topP(),
                d.topK(),
                d.minP(),
                System.nanoTime());
    }
}
