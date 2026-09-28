package com.example.edusafe;

import android.text.TextUtils;
import android.util.Patterns;

public final class EmailValidator {

    public static final String ALLOWED_DOMAIN = "@vossie.net";

    private EmailValidator() { }

    /**
     * Returns true only if the string is a well-formed email address
     * AND ends with the required @vossie.net domain.
     */
    public static boolean isValidVossieEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            return false;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return false;
        }
        return email.trim().toLowerCase().endsWith(ALLOWED_DOMAIN);
    }
}
