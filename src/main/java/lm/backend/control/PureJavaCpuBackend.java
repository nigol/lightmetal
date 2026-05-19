package lm.backend.control;

import java.nio.file.Path;

public final class PureJavaCpuBackend implements Backend {

    @Override
    public Model loadModel(Path gguf) {
        throw new UnsupportedOperationException("PureJavaCpuBackend planned for lightmetal v1.2 (Vector API).");
    }
}
