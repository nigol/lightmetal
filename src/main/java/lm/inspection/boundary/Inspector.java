package lm.inspection.boundary;

import module java.base;
import lm.inspection.control.GGUFReader;
import lm.inspection.entity.GGUFMetadata;

public interface Inspector {

    static GGUFMetadata inspect(Path gguf) {
        return GGUFReader.read(gguf);
    }
}
