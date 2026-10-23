package com.example.edusafe;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.SharedPreferences;
import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Offline-first sync: reports and contacts are saved on the phone first, then sent when there is a connection
public class SyncManager {
    private static final String TAG = "EduSafe";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    // Safe to call from the main thread. onDone runs on the main thread and may be null.
    public static void syncAll(Context ctx, final Runnable onDone) {
        final Context app = ctx.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) {
            if (onDone != null) Net.main(onDone);
            return;
        }
        Net.bg(() -> {
            try {
                pushReports(app);
                refreshStatuses(app);
                syncContacts(app);
            } catch (Exception e) {
                Log.e(TAG, "Sync failed", e);
            } finally {
                RUNNING.set(false);
            }
            if (onDone != null) Net.main(onDone);
        });
    }

    private static final OkHttpClient HTTP = new OkHttpClient();
    private static final MediaType JSON_TYPE = MediaType.get("application/json; charset=utf-8");

    // Supabase Authentication is the source of identity. Never use the publishable key as a user token.
    private static String token(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("edusafe_supabase_session", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() >= prefs.getLong("expires_at", 0)) return null;
        String value = prefs.getString("access_token", "");
        return value == null || value.isEmpty() ? null : value;
    }

    private static Request.Builder authorized(String endpoint, String jwt) {
        return new Request.Builder()
                .url(SupabaseConfig.SUPABASE_URL + endpoint)
                .header("apikey", SupabaseConfig.SUPABASE_KEY)
                .header("Authorization", "Bearer " + jwt);
    }

    private static String getCurrentStudentId(String jwt) throws Exception {
        Request userRequest = authorized("/auth/v1/user", jwt).get().build();
        String uid;
        try (Response response = HTTP.newCall(userRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            uid = new JSONObject(response.body().string()).optString("id", "");
        }
        if (uid.isEmpty()) return null;
        // The user_roles RLS policy only returns the signed-in user's role.
        Request roleRequest = authorized("/rest/v1/user_roles?select=role", jwt).get().build();
        try (Response response = HTTP.newCall(roleRequest).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JSONArray roles = new JSONArray(response.body().string());
            if (roles.length() != 1 || !"student".equals(roles.getJSONObject(0).optString("role"))) return null;
        }
        return uid;
    }

    // Upload local reports as the signed-in student. RLS validates ownership and status.
    private static void pushReports(Context ctx) {
        String jwt = token(ctx);
        if (jwt == null) return;
        try {
            String uid = getCurrentStudentId(jwt);
            if (uid == null) return;
            DatabaseHelper db = DatabaseHelper.get(ctx);
            for (DatabaseHelper.Report r : db.getPendingReports()) {
                JSONObject report = new JSONObject()
                        .put("user_id", uid)
                        .put("tracking_code", r.trackingCode)
                        .put("type", r.type)
                        .put("description", r.description)
                        .put("location", r.location)
                        .put("status", "Received");
                if (r.latitude != null) report.put("latitude", r.latitude);
                if (r.longitude != null) report.put("longitude", r.longitude);
                // Upload attachment first; do not mark the report synced until both steps succeed.
                if (r.photoUri != null && !r.photoUri.trim().isEmpty()) {
                    File file = new File(r.photoUri);
                    if (!file.isFile()) {
                        Log.w(TAG, "Report attachment missing locally: " + r.trackingCode);
                        return;
                    }
                    boolean video = r.photoUri.toLowerCase(java.util.Locale.ROOT).endsWith(".mp4");
                    String ext = video ? ".mp4" : ".jpg";
                    String mime = video ? "video/mp4" : "image/jpeg";
                    String storagePath = uid + "/" + r.trackingCode + ext;
                    if (!uploadReportMedia(jwt, storagePath, file, mime)) {
                        Log.w(TAG, "Attachment upload deferred: " + r.trackingCode);
                        return;
                    }
                    report.put("media_path", storagePath);
                    report.put("media_type", mime);
                }
                JSONArray payload = new JSONArray().put(report);
                Request request = authorized("/rest/v1/crime_reports", jwt)
                        .header("Content-Type", "application/json")
                        .header("Prefer", "return=minimal")
                        .post(RequestBody.create(payload.toString(), JSON_TYPE))
                        .build();
                try (Response response = HTTP.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        db.markReportSynced(r.id);
                    } else if (response.code() == 409 && reportExists(jwt, r.trackingCode)) {
                        // An earlier upload may have succeeded before its acknowledgement was saved.
                        db.markReportSynced(r.id);
                    } else {
                        Log.w(TAG, "Supabase report upload failed for " + r.trackingCode
                                + " (HTTP " + response.code() + ")");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Supabase report sync deferred", e);
        }
    }


    // Private Supabase Storage. Same path on retries prevents duplicate attachments.
    private static boolean uploadReportMedia(String jwt, String storagePath, File file, String mime) {
        try {
            Request request = authorized("/storage/v1/object/report-media/" + storagePath, jwt)
                    .header("Content-Type", mime)
                    .post(RequestBody.create(file, MediaType.parse(mime)))
                    .build();
            try (Response response = HTTP.newCall(request).execute()) {
                if (response.isSuccessful()) return true;
                // The previous upload may have worked before the report row was saved.
                if (response.code() == 409) {
                    Request check = authorized("/storage/v1/object/report-media/" + storagePath, jwt)
                            .get().build();
                    try (Response existing = HTTP.newCall(check).execute()) {
                        return existing.isSuccessful();
                    }
                }
                Log.w(TAG, "Storage upload HTTP " + response.code());
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "Storage upload deferred", e);
            return false;
        }
    }

    private static boolean reportExists(String jwt, String code) throws IOException {
        String encoded = URLEncoder.encode(code, "UTF-8");
        Request request = authorized("/rest/v1/crime_reports?select=id&tracking_code=eq." + encoded, jwt)
                .get().build();
        try (Response response = HTTP.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return false;
            try { return new JSONArray(response.body().string()).length() > 0; }
            catch (Exception ignored) { return false; }
        }
    }

    // Retrieve statuses from Supabase for the signed-in student only.
    private static void refreshStatuses(Context ctx) {
        String jwt = token(ctx);
        if (jwt == null) return;
        try {
            if (getCurrentStudentId(jwt) == null) return;
            DatabaseHelper db = DatabaseHelper.get(ctx);
            SQLiteDatabase writable = db.getWritableDatabase();
            for (DatabaseHelper.Report r : db.getReports("All")) {
                if (!"synced".equals(r.syncStatus)) continue;
                String encoded = URLEncoder.encode(r.trackingCode, "UTF-8");
                Request request = authorized("/rest/v1/crime_reports?select=status&tracking_code=eq." + encoded, jwt)
                        .get().build();
                try (Response response = HTTP.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    JSONArray rows = new JSONArray(response.body().string());
                    if (rows.length() != 1) continue;
                    String status = rows.getJSONObject(0).optString("status", r.status);
                    if (!status.equals(r.status)) {
                        ContentValues values = new ContentValues();
                        values.put("status", status);
                        writable.update("reports", values, "id=?", new String[]{String.valueOf(r.id)});
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Report status refresh deferred", e);
        }
    }

    // Keep trusted contacts backed up on the server. On a fresh install, restore them from the server.
    private static void syncContacts(Context ctx) {
        DatabaseHelper db = DatabaseHelper.get(ctx);
        List<DatabaseHelper.Contact> local = db.getContacts();
        ApiClient.Result remote = ApiClient.get(ctx, "/contacts");
        if (!remote.ok() || remote.json == null) return;

        JSONArray server = remote.json.optJSONArray("contacts");
        boolean firstSync = !Session.getFlag(ctx, "contacts_pulled");
        if (firstSync) {
            Session.setFlag(ctx, "contacts_pulled", true);
            if (local.size() <= 1 && server != null && server.length() > 1) {
                replaceLocal(db, server);
                return;
            }
        }
        try {
            JSONArray out = new JSONArray();
            for (DatabaseHelper.Contact c : local) {
                out.put(new JSONObject()
                        .put("name", c.name)
                        .put("phone", c.phone)
                        .put("relationship", c.relationship)
                        .put("priority", c.priority));
            }
            ApiClient.Result res = ApiClient.send(ctx, "PUT", "/contacts", new JSONObject().put("contacts", out));
            if (!res.ok() && !res.offline()) Log.w(TAG, "Contacts not saved on server: " + res.error);
        } catch (Exception e) {
            Log.e(TAG, "Contacts sync failed", e);
        }
    }

    private static void replaceLocal(DatabaseHelper db, JSONArray server) {
        SQLiteDatabase w = db.getWritableDatabase();
        w.beginTransaction();
        try {
            w.delete("contacts", null, null);
            for (int i = 0; i < server.length(); i++) {
                JSONObject o = server.getJSONObject(i);
                String rel = o.optString("relationship");
                ContentValues cv = new ContentValues();
                cv.put("name", o.optString("name"));
                cv.put("phone", o.optString("phone"));
                cv.put("relationship", rel);
                cv.put("priority", o.optInt("priority", i + 1));
                cv.put("locked", "Campus Security".equals(rel) ? 1 : 0);
                w.insert("contacts", null, cv);
            }
            w.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Restoring contacts failed", e);
        } finally {
            w.endTransaction();
        }
    }
}

