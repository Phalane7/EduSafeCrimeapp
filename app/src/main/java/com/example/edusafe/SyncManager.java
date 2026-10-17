package com.example.edusafe;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
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

    // Send queued reports
    private static void pushReports(Context ctx) {
        DatabaseHelper db = DatabaseHelper.get(ctx);
        for (DatabaseHelper.Report r : db.getPendingReports()) {
            ApiClient.Multipart mp = new ApiClient.Multipart()
                    .field("trackingCode", r.trackingCode)
                    .field("type", r.type)
                    .field("description", r.description)
                    .field("locationLabel", r.location)
                    .field("createdAt", String.valueOf(r.createdAt))
                    .field("fcmToken", Session.getString(ctx, "fcm_token"));
            if (r.latitude != null) mp.field("latitude", String.valueOf(r.latitude));
            if (r.longitude != null) mp.field("longitude", String.valueOf(r.longitude));
            if (r.photoUri != null && new File(r.photoUri).exists()) {
                mp.file("media", r.photoUri, r.photoUri.endsWith(".mp4") ? "video/mp4" : "image/jpeg");
            }
            ApiClient.Result res = ApiClient.upload(ctx, "/reports", mp);
            if (res.ok()) {
                db.markReportSynced(r.id);
            } else if (res.offline() || res.code >= 500 || res.code == 401 || res.code == 429) {
                return; // try again on the next sync
            } else {
                Log.w(TAG, "Server rejected report " + r.trackingCode + ": " + res.error);
            }
        }
    }

    // Pull status changes made by guards (Under Review, Verified, Resolved...)
    private static void refreshStatuses(Context ctx) {
        DatabaseHelper db = DatabaseHelper.get(ctx);
        SQLiteDatabase w = db.getWritableDatabase();
        for (DatabaseHelper.Report r : db.getReports("All")) {
            if (!"synced".equals(r.syncStatus) || "Resolved".equals(r.status) || "Rejected".equals(r.status)) continue;
            ApiClient.Result res = ApiClient.get(ctx, "/reports/" + r.trackingCode);
            if (res.offline()) return;
            if (res.ok() && res.json != null) {
                String s = res.json.optString("status", r.status);
                if (!s.equals(r.status)) {
                    ContentValues cv = new ContentValues();
                    cv.put("status", s);
                    w.update("reports", cv, "tracking_code=?", new String[]{r.trackingCode});
                }
            }
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
