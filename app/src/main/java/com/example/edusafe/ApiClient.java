package com.example.edusafe;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApiClient {
    private static final String TAG = "EduSafe";

    // 10.0.2.2 is how the emulator reaches your laptop. On a real phone use your laptop's Wi-Fi address.
    public static final String BASE_URL = "http://10.0.2.2:3000/api";

    public static class Result {
        public int code = 0;      // 0 = could not reach the server
        public JSONObject json;
        public String error;

        public boolean ok() {
            return code >= 200 && code < 300;
        }

        public boolean offline() {
            return code == 0;
        }
    }

    public static class Multipart {
        private final Map<String, String> fields = new LinkedHashMap<>();
        private String fileField, filePath, fileMime;

        public Multipart field(String key, String value) {
            if (value != null) fields.put(key, value);
            return this;
        }

        public Multipart file(String field, String path, String mime) {
            fileField = field;
            filePath = path;
            fileMime = mime;
            return this;
        }

        void write(OutputStream os, String boundary) throws IOException {
            String nl = "\r\n";
            for (Map.Entry<String, String> e : fields.entrySet()) {
                os.write(("--" + boundary + nl + "Content-Disposition: form-data; name=\"" + e.getKey() + "\""
                        + nl + nl).getBytes(StandardCharsets.UTF_8));
                os.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                os.write(nl.getBytes(StandardCharsets.UTF_8));
            }
            if (filePath != null) {
                File f = new File(filePath);
                if (f.exists()) {
                    os.write(("--" + boundary + nl + "Content-Disposition: form-data; name=\"" + fileField
                            + "\"; filename=\"" + f.getName() + "\"" + nl + "Content-Type: " + fileMime
                            + nl + nl).getBytes(StandardCharsets.UTF_8));
                    try (InputStream in = new FileInputStream(f)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    os.write(nl.getBytes(StandardCharsets.UTF_8));
                }
            }
            os.write(("--" + boundary + "--" + nl).getBytes(StandardCharsets.UTF_8));
        }
    }

    // ---------- Public calls (always call these from a background thread) ----------
    public static Result get(Context ctx, String path) {
        return call(ctx, "GET", path, null, null, true);
    }

    public static Result getPublic(Context ctx, String path) {
        return once(ctx, "GET", path, null, null, false);
    }

    public static Result send(Context ctx, String method, String path, JSONObject body) {
        return call(ctx, method, path, body, null, true);
    }

    public static Result upload(Context ctx, String path, Multipart mp) {
        return call(ctx, "POST", path, null, mp, true);
    }

    // ---------- Internals ----------
    private static Result call(Context ctx, String method, String path, JSONObject body, Multipart mp, boolean auth) {
        if (auth && Session.token(ctx) == null && !login(ctx)) {
            Result r = new Result();
            r.error = "No connection to the server.";
            return r;
        }
        Result r = once(ctx, method, path, body, mp, auth);
        if (auth && r.code == 401 && login(ctx)) r = once(ctx, method, path, body, mp, auth);
        return r;
    }

    // Logs this phone in, creating an anonymous account the first time
    private static synchronized boolean login(Context ctx) {
        try {
            String email = Session.email(ctx);
            String pw = Session.password(ctx);
            if (email != null && pw != null) {
                JSONObject body = new JSONObject().put("email", email).put("password", pw);
                Result r = once(ctx, "POST", "/auth/login", body, null, false);
                if (r.ok() && r.json != null) {
                    Session.setToken(ctx, r.json.getString("token"));
                    return true;
                }
                if (r.offline()) return false;
            }
            email = "device-" + Session.randomString(10) + "@edusafe.local";
            pw = Session.randomString(24);
            JSONObject body = new JSONObject().put("email", email).put("password", pw).put("name", "Student");
            Result r = once(ctx, "POST", "/auth/register", body, null, false);
            if (r.ok() && r.json != null) {
                Session.saveCredentials(ctx, email, pw);
                Session.setToken(ctx, r.json.getString("token"));
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Login failed", e);
        }
        return false;
    }

    private static Result once(Context ctx, String method, String path, JSONObject body, Multipart mp, boolean auth) {
        Result res = new Result();
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
            c.setConnectTimeout(6000);
            c.setReadTimeout(20000);
            c.setRequestMethod(method);
            c.setRequestProperty("Accept", "application/json");
            if (auth) {
                String t = Session.token(ctx);
                if (t != null) c.setRequestProperty("Authorization", "Bearer " + t);
            }
            if (body == null && mp == null && ("POST".equals(method) || "PUT".equals(method))) {
                body = new JSONObject();
            }
            if (mp != null) {
                String boundary = "----EduSafe" + System.currentTimeMillis();
                c.setDoOutput(true);
                c.setChunkedStreamingMode(8192);
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream os = new BufferedOutputStream(c.getOutputStream())) {
                    mp.write(os, boundary);
                }
            } else if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            res.code = c.getResponseCode();
            InputStream is = res.code >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = is == null ? "" : read(is);
            if (!text.isEmpty()) {
                try {
                    res.json = new JSONObject(text);
                } catch (JSONException ignored) {
                    // not JSON
                }
            }
            if (!res.ok()) {
                res.error = (res.json != null && res.json.has("error"))
                        ? res.json.optString("error")
                        : "Something went wrong (error " + res.code + ").";
            }
        } catch (IOException e) {
            res.code = 0;
            res.error = "No connection to the server.";
        } catch (Exception e) {
            Log.e(TAG, "Request failed", e);
            res.code = 0;
            res.error = "Something went wrong. Please try again.";
        } finally {
            if (c != null) c.disconnect();
        }
        return res;
    }

    private static String read(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toString("UTF-8");
        }
    }
}
