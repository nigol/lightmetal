package lm.catalog.boundary;

import module java.base;

import lm.catalog.control.ModelsDirectory;

public interface ModelCatalog {

    String EXTENSION = ".gguf";

    static List<String> list() {
        var dir = ModelsDirectory.path();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (var entries = Files.list(dir)) {
            return entries
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .filter(ModelCatalog::isModel)
                    .sorted()
                    .toList();
        } catch (IOException problem) {
            throw new IllegalStateException("cannot list models in " + dir, problem);
        }
    }

    static Path resolve(String fileName) {
        return ModelsDirectory.path().resolve(fileName);
    }

    static List<String> search(String fragment) {
        var lowerFragment = fragment.toLowerCase();
        return list().stream()
                .filter(name -> name.toLowerCase().contains(lowerFragment))
                .toList();
    }

    static boolean isModel(String name) {
        return name.toLowerCase().endsWith(EXTENSION);
    }
}
