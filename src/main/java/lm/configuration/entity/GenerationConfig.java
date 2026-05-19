package lm.configuration.entity;

public record GenerationConfig(
        int maxTokens,
        float temperature,
        float topP,
        int topK,
        float minP,
        long seed) {

    public static GenerationConfig defaults() {
        return new GenerationConfig(256, 0.7f, 0.9f, 40, 0.05f, System.nanoTime());
    }
}
