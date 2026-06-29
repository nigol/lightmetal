package lm.generation.boundary;

import module java.base;
import lm.catalog.boundary.ModelCatalog;
import lm.configuration.control.ZCfg;
import lm.configuration.entity.GenerationConfig;
import lm.generation.entity.Tps;
import lm.logging.control.Log;

/**
 * Zero-coupling embedding boundary for one-shot text completion: a plain
 * {@code BinaryOperator<String>} — {@code (model, prompt) -> generated text}.
 * Hosts discover it via {@link java.util.ServiceLoader} and drive it through
 * {@code String} alone, never importing {@code lm.*} types; config is
 * republished as system properties for {@code System.getProperty(...)} access.
 *
 * <p>Stateless per call: each invocation resolves the model, loads it, streams
 * the completion, and closes it again — no session is retained (contrast
 * {@link LightMetalChat}, which keeps a warm single-model session). Reasoning
 * ({@code "thought"}) tokens are dropped from the result; only the visible
 * answer is returned, with throughput logged for multi-token runs.
 */
public final class LightMetalText implements BinaryOperator<String> {

    // Eager so embedders can read config (model.directory, defaults, etc.) the moment
    // ServiceLoader discovers this provider. loadAndPublish also republishes the values
    // as system properties so zero-coupling hosts use System.getProperty(...) — no lm.* imports.
    public LightMetalText() {
        ZCfg.loadAndPublish("lightmetal");
    }

    @Override
    public String apply(String model, String prompt) {
        return run(model, prompt, GenerationConfig.fromProperties());
    }

    public String run(String model, String prompt, GenerationConfig config) {
        var out = new StringBuilder();
        var count = new AtomicLong();
        var startNanos = new AtomicLong();
        // Route through ModelCatalog so bare filenames resolve under models.directory
        // (matching the CLI's -model flag). Absolute paths pass through unchanged.
        var modelPath = ModelCatalog.resolve(model);
        Log.system("[model loaded from %s]".formatted(modelPath.toString()));
        try (var lm = LightMetal.load(modelPath);
             var stream = lm.generate(prompt, config)) {
            stream.forEach(t -> {
                startNanos.compareAndSet(0L, System.nanoTime());
                count.incrementAndGet();
                if ("thought".equals(t.channel())) return;
                out.append(t.text());
            });
        }
        if (count.get() > 1)
            Log.system("[" + Tps.measure(count.get(), startNanos.get()) + "]");
        return out.toString();
    }
}
