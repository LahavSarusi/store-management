package com.store.server.dao.json;

import com.store.server.dao.ConfigDao;
import com.store.server.model.PasswordPolicy;

import java.nio.file.Path;

/** Server-wide settings in {@code config.json}. */
public class JsonConfigDao implements ConfigDao {

    /** File layout; add new settings as fields here. */
    private static final class Config {
        PasswordPolicy passwordPolicy = PasswordPolicy.defaults();
    }

    private final Path file;
    private final Config config;

    public JsonConfigDao(Path file) {
        this.file = file;
        this.config = JsonFileStore.read(file, Config.class, Config::new);
        if (config.passwordPolicy == null) {
            config.passwordPolicy = PasswordPolicy.defaults();
        }
    }

    @Override
    public synchronized PasswordPolicy loadPasswordPolicy() {
        return config.passwordPolicy;
    }

    @Override
    public synchronized void savePasswordPolicy(PasswordPolicy policy) {
        config.passwordPolicy = policy;
        JsonFileStore.write(file, config);
    }
}
