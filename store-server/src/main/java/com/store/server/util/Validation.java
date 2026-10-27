package com.store.server.util;

import com.store.server.protocol.DomainException;
import com.store.server.protocol.ErrorCode;

import java.util.regex.Pattern;

/** Input validation helpers. Failures surface to the client as BAD_REQUEST. */
public final class Validation {

    /** Israeli mobile/landline: 9-10 digits starting with 0, optional dash after the prefix. */
    private static final Pattern PHONE = Pattern.compile("0\\d{1,2}-?\\d{7}");
    private static final Pattern USERNAME = Pattern.compile("[a-zA-Z0-9._]{3,30}");
    /** Bank-branch-account style, e.g. {@code 12-345-678901}: digits with optional dashes. */
    private static final Pattern BANK_ACCOUNT = Pattern.compile("\\d[\\d-]{2,18}\\d");

    private Validation() { }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainException(ErrorCode.BAD_REQUEST, message);
        }
    }

    public static String requireText(String value, String field) {
        require(value != null && !value.isBlank(), field + " is required");
        return value.trim();
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    public static boolean isValidBankAccount(String account) {
        return account != null && BANK_ACCOUNT.matcher(account).matches();
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    /** Israeli ID (Teudat Zehut): up to 9 digits with a Luhn-style check digit. */
    public static boolean isValidIsraeliId(String id) {
        if (id == null || !id.matches("\\d{5,9}")) {
            return false;
        }
        String padded = "0".repeat(9 - id.length()) + id;
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            int digit = (padded.charAt(i) - '0') * ((i % 2) + 1);
            sum += digit > 9 ? digit - 9 : digit;
        }
        return sum % 10 == 0;
    }
}
