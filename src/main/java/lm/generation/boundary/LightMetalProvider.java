package lm.generation.boundary;

import module java.base;
import lm.configuration.entity.GenerationConfig;
import lm.generation.entity.Tps;
import lm.logging.control.Log;

public final class LightMetalProvider implements BinaryOperator<String> {

    @Override
    public String apply(String model, String prompt) {
        return run(model, prompt, GenerationConfig.defaults());
    }

    public String run(String model, String prompt, GenerationConfig cfg) {
        var out = new StringBuilder();
        var count = new AtomicLong();
        var startNanos = new AtomicLong();
        try (var lm = LightMetal.load(Path.of(model));
             var stream = lm.generate(prompt, cfg)) {
            stream.forEach(t -> {
                startNanos.compareAndSet(0L, System.nanoTime());
                count.incrementAndGet();
                out.append(t.text());
            });
        }
        if (count.get() > 1)
            Log.system("[" + Tps.measure(count.get(), startNanos.get()) + "]");
        return out.toString();
    }
}
