package com.store.server.dao;

import com.store.server.model.PasswordPolicy;

public interface ConfigDao {
    PasswordPolicy loadPasswordPolicy();
    void savePasswordPolicy(PasswordPolicy policy);
}
