package lm.generation.boundary;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import lm.backend.control.Context;
import lm.backend.control.Model;
import lm.configuration.control.ZCfg;
import lm.configuration.entity.ContextParams;
import lm.configuration.entity.GenerationConfig;
import lm.configuration.entity.Token;
import lm.http.entity.AnthropicMessagesRequest.UserText;
import lm.inspection.boundary.Inspector;
import lm.inspection.entity.GGUFMetadata;
import lm.logging.control.Log;
import lm.prompting.control.ChatTemplate;

public final class LightMetal implements AutoCloseable {

    private final Model model;
    private final Context ctx;
    private final GGUFMetadata metadata;
    private final boolean addBos;

    private LightMetal(Model model, Context ctx, GGUFMetadata metadata) {
        this.model = model;
        this.ctx = ctx;
        this.metadata = metadata;
        this.addBos = metadata.addBosToken().orElse(true);
    }

    public static LightMetal load(Path gguf) {
        var metadata = readMetadata(gguf);
        var model = new Model(gguf);
        var ctx = model.newContext(contextParams(metadata));
        return new LightMetal(model, ctx, metadata);
    }

    public GGUFMetadata metadata() {
        return metadata;
    }

    public Stream<Token> generate(String userPrompt, GenerationConfig cfg) {
        var name = metadata.detectTemplate().orElse("mistral4");
        var rendered = ChatTemplate.of(name).render("", List.of(),
                List.of(new UserText(userPrompt)));
        return complete(rendered, cfg);
    }

    public Stream<Token> complete(String formattedPrompt, GenerationConfig cfg) {
        var promptTokens = ctx.tokenize(formattedPrompt, addBos);
        return ctx.generate(promptTokens, cfg);
    }

    public void reset() {
        ctx.resetKvCache();
    }

    @Override
    public void close() {
        ctx.close();
        model.close();
    }

    private static GGUFMetadata readMetadata(Path gguf) {
        try {
            return Inspector.inspect(gguf);
        } catch (RuntimeException e) {
            Log.system("[inspector] could not read metadata: " + e.getMessage() + " — using built-in defaults");
            return GGUFMetadata.empty();
        }
    }

    private static ContextParams contextParams(GGUFMetadata meta) {
        var d = ContextParams.defaults();
        var defaultCtxLen = (int) Math.min(meta.contextLength().orElse(d.contextLength()), Integer.MAX_VALUE);
        return new ContextParams(
                ZCfg.integer("context.length", defaultCtxLen),
                ZCfg.integer("context.batch.size", d.batchSize()),
                ZCfg.integer("context.gpu.layers", d.gpuLayers()),
                ZCfg.integer("context.seed", (int) d.seed()));
    }
}
