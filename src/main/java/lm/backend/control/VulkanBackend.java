package lm.backend.control;

import java.nio.file.Path;

public final class VulkanBackend implements Backend {

    @Override
    public Model loadModel(Path gguf) {
        throw new UnsupportedOperationException("VulkanBackend planned for lightmetal v2 (MoltenVK + SPIR-V).");
    }
}
