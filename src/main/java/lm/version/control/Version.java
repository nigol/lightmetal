package lm.version.control;

import module java.base;

import lm.logging.control.Log;

public interface Version {

    String VALUE = resolve();

    static String resolve() {
        var manifest = Version.class.getPackage().getImplementationVersion();
        if (manifest != null && !manifest.isBlank()) return manifest.strip();
        var resource = readResource();
        if (resource != null) return resource;
        return readFile();
    }

    static String readResource() {
        try (var in = Version.class.getClassLoader().getResourceAsStream("version.txt")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            Log.system("[version: resource read failed: " + e.getMessage() + "]");
            return null;
        }
    }

    static String readFile() {
        var path = Path.of("version.txt");
        if (!Files.exists(path)) return "dev";
        try {
            return Files.readString(path).strip();
        } catch (IOException e) {
            Log.system("[version: file read failed: " + e.getMessage() + "]");
            return "dev";
        }
    }
}
