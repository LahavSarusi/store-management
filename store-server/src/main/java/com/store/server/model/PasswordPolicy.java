package com.store.server.model;

import java.util.ArrayList;
import java.util.List;

/** Admin-configurable password rules. {@code expiryDays == 0} means passwords never expire. */
public record PasswordPolicy(int minLength,
                             boolean requireDigit,
                             boolean requireUppercase,
                             boolean requireSpecial,
                             int expiryDays) {

    public static PasswordPolicy defaults() {
        return new PasswordPolicy(8, true, true, false, 90);
    }

    /** @return human-readable list of violated rules, empty when the password is acceptable */
    public List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.length() < minLength) {
            problems.add("at least " + minLength + " characters");
        }
        if (password == null) {
            return problems;
        }
        if (requireDigit && password.chars().noneMatch(Character::isDigit)) {
            problems.add("at least one digit");
        }
        if (requireUppercase && password.chars().noneMatch(Character::isUpperCase)) {
            problems.add("at least one uppercase letter");
        }
        if (requireSpecial && password.chars().allMatch(Character::isLetterOrDigit)) {
            problems.add("at least one special character");
        }
        return problems;
    }

    public boolean isExpired(long passwordChangedAtMillis, long nowMillis) {
        return expiryDays > 0 && nowMillis - passwordChangedAtMillis > expiryDays * 86_400_000L;
    }
}
