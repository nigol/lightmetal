package lm.catalog.control;

import module java.base;

import lm.configuration.control.ZCfg;

public interface ModelsDirectory {

    String KEY = "models.directory";
    String DEFAULT = "~/models";

    static Path path() {
        var configured = ZCfg.string(KEY, DEFAULT);
        var home = System.getProperty("user.home");
        if (configured.equals("~")) {
            return Path.of(home);
        }
        if (configured.startsWith("~/")) {
            return Path.of(home, configured.substring(2));
        }
        return Path.of(configured);
    }
}
