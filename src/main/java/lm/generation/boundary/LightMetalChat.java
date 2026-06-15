package lm.generation.boundary;

import module java.base;

import org.json.JSONObject;
import org.json.JSONTokener;

import lm.catalog.boundary.ModelCatalog;
import lm.configuration.control.ZCfg;
import lm.configuration.entity.GenerationConfig;
import lm.http.control.MessagesHandler;
import lm.http.entity.AnthropicMessagesRequest;
import lm.logging.control.Log;

public final class LightMetalChat implements UnaryOperator<String>, AutoCloseable {

    LightMetal lm;
    Path loadedPath;

    // Eager so embedders can read config (model.directory, defaults, etc.) the moment
    // ServiceLoader discovers this provider. loadAndPublish also republishes the values
    // as system properties so zero-coupling hosts use System.getProperty(...) — no lm.* imports.
    public LightMetalChat() {
        ZCfg.loadAndPublish("lightmetal");
    }

    @Override
    public synchronized String apply(String requestJson) {
        var root = new JSONObject(new JSONTokener(requestJson));
        // Route through ModelCatalog so bare filenames (e.g. "gemma-4-26B...gguf")
        // resolve under models.directory the same way the CLI's -model flag does.
        // Absolute paths pass through unchanged. Fall back to ZCfg so embedders
        // (e.g. zsmith) can omit `model` and let lightmetal's own config own it.
        var modelName = root.optString("model", null);
        if (modelName == null || modelName.isBlank()) {
            modelName = ZCfg.string("model");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException(
                    "no model specified — set `model` in the request payload or in "
                            + "~/.lightmetal/app.properties");
        }
        var modelPath = ModelCatalog.resolve(modelName);
        ensureLoaded(modelPath);
        var req = AnthropicMessagesRequest.from(root, GenerationConfig.fromProperties());
        var resp = lm.chat(req.system(), req.tools(), req.turns(), config(req));
        return MessagesHandler.toAnthropicJson(resp,
                lm.metadata().name().orElse("lightmetal")).toString();
    }

    @Override
    public synchronized void close() {
        if (lm == null) return;
        lm.close();
        lm = null;
        loadedPath = null;
    }

    // Single-model session: the first apply() decides the path; a later apply()
    // with a different path swaps (closes old, loads new). zsmith-style agents
    // keep the same path forever and hit the fast path on every turn.
    void ensureLoaded(Path modelPath) {
        if (lm != null && modelPath.equals(loadedPath)) return;
        if (lm != null) {
            Log.system("[swapping model %s -> %s]".formatted(loadedPath, modelPath));
            lm.close();
        }
        lm = LightMetal.load(modelPath);
        loadedPath = modelPath;
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
