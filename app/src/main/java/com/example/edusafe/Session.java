package com.example.edusafe;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.SecureRandom;

// Holds the login token. The anonymous phone account is temporary:
// when your team's login screen is ready, call setToken() with the real token.
public class Session {
    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("edusafe_session", Context.MODE_PRIVATE);
    }

    public static String token(Context c) {
        return p(c).getString("token", null);
    }

    public static void setToken(Context c, String t) {
        p(c).edit().putString("token", t).apply();
    }

    public static String email(Context c) {
        return p(c).getString("email", null);
    }

    public static String password(Context c) {
        return p(c).getString("password", null);
    }

    public static void saveCredentials(Context c, String email, String password) {
        p(c).edit().putString("email", email).putString("password", password).apply();
    }

    public static String getString(Context c, String key) {
        return p(c).getString(key, null);
    }

    public static void putString(Context c, String key, String value) {
        p(c).edit().putString(key, value).apply();
    }

    public static boolean getFlag(Context c, String key) {
        return p(c).getBoolean(key, false);
    }

    public static void setFlag(Context c, String key, boolean value) {
        p(c).edit().putBoolean(key, value).apply();
    }

    public static String randomString(int n) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }
}