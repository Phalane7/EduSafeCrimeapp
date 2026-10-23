package com.example.edusafe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "EduSafe";

    private static final String DB_NAME = "edusafe.db";

    // Keep the existing database version to preserve saved data.
    private static final int DB_VERSION = 3;

    // 1 Campus Security + 4 personal contacts.
    public static final int MAX_CONTACTS = 5;

    private static final String SECURITY_NAME = "Campus Security";
    private static final String SECURITY_PHONE = "0713247336";

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper get(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(
                    context.getApplicationContext()
            );
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ============================================================
    // DATA MODELS
    // ============================================================

    public static class Incident {
        public long id;
        public String type, title, description;
        public String location, status, photoUrl;
        public long createdAt;
    }

    public static class Report {
        public long id;
        public String trackingCode, type, description;
        public String location, photoUri, status, syncStatus;
        public Double latitude, longitude;
        public long createdAt;
    }

    public static class Contact {
        public long id;
        public String name, phone, relationship;
        public int priority;
        public boolean locked;
    }

    // ============================================================
    // DATABASE CREATION
    // ============================================================

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL(
                "CREATE TABLE incidents (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "type TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "description TEXT, " +
                        "location TEXT, " +
                        "status TEXT NOT NULL, " +
                        "photo_url TEXT, " +
                        "created_at INTEGER NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE reports (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "tracking_code TEXT NOT NULL UNIQUE, " +
                        "type TEXT NOT NULL, " +
                        "description TEXT NOT NULL, " +
                        "location TEXT, " +
                        "latitude REAL, " +
                        "longitude REAL, " +
                        "photo_uri TEXT, " +
                        "status TEXT NOT NULL DEFAULT 'Received', " +
                        "sync_status TEXT NOT NULL DEFAULT 'pending', " +
                        "created_at INTEGER NOT NULL)"
        );

        db.execSQL(
                "CREATE TABLE contacts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "name TEXT NOT NULL, " +
                        "phone TEXT NOT NULL, " +
                        "relationship TEXT NOT NULL, " +
                        "priority INTEGER NOT NULL, " +
                        "locked INTEGER NOT NULL DEFAULT 0)"
        );

        // Create the permanent Campus Security contact.
        ContentValues security = new ContentValues();

        security.put("name", SECURITY_NAME);
        security.put("phone", SECURITY_PHONE);
        security.put("relationship", "Campus Security");
        security.put("priority", 1);
        security.put("locked", 1);

        db.insert("contacts", null, security);

        Log.d(TAG, "Database created with Campus Security");
    }

    // ============================================================
    // DATABASE UPGRADES
    // ============================================================

    @Override
    public void onUpgrade(
            SQLiteDatabase db,
            int oldVersion,
            int newVersion) {

        if (oldVersion < 2) {
            db.execSQL(
                    "UPDATE contacts SET phone='' " +
                            "WHERE locked=1 AND phone='0111112222'"
            );
        }

        if (oldVersion < 3) {
            // Existing records are preserved.
        }
    }

    // ============================================================
    // INCIDENTS
    // ============================================================

    public List<Incident> getIncidents(
            String status,
            String type,
            int sinceDays) {

        List<String> conditions = new ArrayList<>();
        List<String> arguments = new ArrayList<>();

        if (status != null) {
            conditions.add("status=?");
            arguments.add(status);
        }

        if (type != null && !type.equals("All")) {
            conditions.add("type=?");
            arguments.add(type);
        }

        if (sinceDays > 0) {
            conditions.add("created_at>=?");
            arguments.add(
                    String.valueOf(
                            System.currentTimeMillis()
                                    - sinceDays * 86400000L
                    )
            );
        }

        String selection = conditions.isEmpty()
                ? null
                : TextUtils.join(" AND ", conditions);

        String[] selectionArgs = arguments.isEmpty()
                ? null
                : arguments.toArray(new String[0]);

        List<Incident> incidents = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                "incidents",
                null,
                selection,
                selectionArgs,
                null,
                null,
                "created_at DESC"
        )) {

            while (cursor.moveToNext()) {

                Incident incident = new Incident();

                incident.id = cursor.getLong(
                        cursor.getColumnIndexOrThrow("id"));

                incident.type = cursor.getString(
                        cursor.getColumnIndexOrThrow("type"));

                incident.title = cursor.getString(
                        cursor.getColumnIndexOrThrow("title"));

                incident.description = cursor.getString(
                        cursor.getColumnIndexOrThrow("description"));

                incident.location = cursor.getString(
                        cursor.getColumnIndexOrThrow("location"));

                incident.status = cursor.getString(
                        cursor.getColumnIndexOrThrow("status"));

                incident.photoUrl = cursor.getString(
                        cursor.getColumnIndexOrThrow("photo_url"));

                incident.createdAt = cursor.getLong(
                        cursor.getColumnIndexOrThrow("created_at"));

                incidents.add(incident);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load incidents", e);
        }

        return incidents;
    }

    // ============================================================
    // REPORT TRACKING CODE
    // ============================================================

    private String makeCode() {

        String characters =
                "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        Random random = new Random();

        StringBuilder code = new StringBuilder("ES-");

        for (int i = 0; i < 6; i++) {
            code.append(
                    characters.charAt(
                            random.nextInt(characters.length())
                    )
            );
        }

        return code.toString();
    }

    // ============================================================
    // CREATE REPORT
    // ============================================================

    public Report createReport(
            String type,
            String description,
            String location,
            Double latitude,
            Double longitude,
            String photoUri) {

        ContentValues values = new ContentValues();

        String trackingCode = makeCode();

        values.put("tracking_code", trackingCode);
        values.put("type", type);
        values.put("description", description);
        values.put("location", location);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("photo_uri", photoUri);
        values.put("status", "Received");
        values.put("sync_status", "pending");
        values.put("created_at", System.currentTimeMillis());

        long id = getWritableDatabase().insert(
                "reports",
                null,
                values
        );

        if (id == -1) {
            return null;
        }

        return getReport(id);
    }

    // ============================================================
    // GET SINGLE REPORT
    // ============================================================

    public Report getReport(long id) {

        try (Cursor cursor = getReadableDatabase().query(
                "reports",
                null,
                "id=?",
                new String[]{String.valueOf(id)},
                null,
                null,
                null
        )) {

            if (cursor.moveToFirst()) {
                return readReport(cursor);
            }
        }

        return null;
    }

    // ============================================================
    // GET REPORTS
    // ============================================================

    public List<Report> getReports(String type) {

        List<Report> reports = new ArrayList<>();

        boolean all = type == null || type.equals("All");

        try (Cursor cursor = getReadableDatabase().query(
                "reports",
                null,
                all ? null : "type=?",
                all ? null : new String[]{type},
                null,
                null,
                "created_at DESC"
        )) {

            while (cursor.moveToNext()) {
                reports.add(readReport(cursor));
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load reports", e);
        }

        return reports;
    }

    // ============================================================
    // UPDATE REPORT STATUS
    // ============================================================

    public boolean updateReportStatus(
            long id,
            String status) {

        if (!"Received".equals(status)
                && !"Under Review".equals(status)
                && !"Resolved".equals(status)) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put("status", status);

        return getWritableDatabase().update(
                "reports",
                values,
                "id=?",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    // ============================================================
    // PENDING REPORTS
    // ============================================================

    public List<Report> getPendingReports() {

        List<Report> reports = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                "reports",
                null,
                "sync_status='pending'",
                null,
                null,
                null,
                "id"
        )) {

            while (cursor.moveToNext()) {
                reports.add(readReport(cursor));
            }
        }

        return reports;
    }

    // ============================================================
    // EDIT PENDING REPORT
    // ============================================================

    public boolean updateReport(
            long id,
            String type,
            String description) {

        ContentValues values = new ContentValues();

        values.put("type", type);
        values.put("description", description);

        return getWritableDatabase().update(
                "reports",
                values,
                "id=? AND sync_status='pending'",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    // ============================================================
    // DELETE PENDING REPORT
    // ============================================================

    public boolean deleteReport(long id) {

        return getWritableDatabase().delete(
                "reports",
                "id=? AND sync_status='pending'",
                new String[]{String.valueOf(id)}
        ) > 0;
    }

    // ============================================================
    // MARK REPORT AS SYNCED
    // ============================================================

    public void markReportSynced(long id) {

        ContentValues values = new ContentValues();

        values.put("sync_status", "synced");

        getWritableDatabase().update(
                "reports",
                values,
                "id=?",
                new String[]{String.valueOf(id)}
        );
    }

    // ============================================================
    // READ REPORT
    // ============================================================

    private Report readReport(Cursor cursor) {

        Report report = new Report();

        report.id = cursor.getLong(
                cursor.getColumnIndexOrThrow("id"));

        report.trackingCode = cursor.getString(
                cursor.getColumnIndexOrThrow("tracking_code"));

        report.type = cursor.getString(
                cursor.getColumnIndexOrThrow("type"));

        report.description = cursor.getString(
                cursor.getColumnIndexOrThrow("description"));

        report.location = cursor.getString(
                cursor.getColumnIndexOrThrow("location"));

        int latIndex =
                cursor.getColumnIndexOrThrow("latitude");

        int lngIndex =
                cursor.getColumnIndexOrThrow("longitude");

        report.latitude = cursor.isNull(latIndex)
                ? null
                : cursor.getDouble(latIndex);

        report.longitude = cursor.isNull(lngIndex)
                ? null
                : cursor.getDouble(lngIndex);

        report.photoUri = cursor.getString(
                cursor.getColumnIndexOrThrow("photo_uri"));

        report.status = cursor.getString(
                cursor.getColumnIndexOrThrow("status"));

        report.syncStatus = cursor.getString(
                cursor.getColumnIndexOrThrow("sync_status"));

        report.createdAt = cursor.getLong(
                cursor.getColumnIndexOrThrow("created_at"));

        return report;
    }

    // ============================================================
    // TRUSTED CONTACTS
    // ============================================================

    // Ensures Campus Security always exists at Priority 1.
    // Preserves personal contacts already saved in the database.
    private void ensureCampusSecurity() {

        SQLiteDatabase database = getWritableDatabase();

        database.beginTransaction();

        try {
            long securityId = -1;

            // Find existing Campus Security contact.
            try (Cursor cursor = database.query(
                    "contacts",
                    new String[]{"id", "name", "locked"},
                    null,
                    null,
                    null,
                    null,
                    "id ASC"
            )) {

                while (cursor.moveToNext()) {

                    long id = cursor.getLong(0);
                    String name = cursor.getString(1);
                    boolean locked = cursor.getInt(2) == 1;

                    if (locked ||
                            SECURITY_NAME.equalsIgnoreCase(name)) {

                        if (securityId == -1) {
                            securityId = id;
                        }
                    }
                }
            }

            ContentValues security = new ContentValues();

            security.put("name", SECURITY_NAME);
            security.put("phone", SECURITY_PHONE);
            security.put("relationship", "Campus Security");
            security.put("priority", 1);
            security.put("locked", 1);

            if (securityId == -1) {

                securityId = database.insertOrThrow(
                        "contacts",
                        null,
                        security
                );

            } else {

                database.update(
                        "contacts",
                        security,
                        "id=?",
                        new String[]{String.valueOf(securityId)}
                );
            }

            // Remove duplicate Campus Security records only.
            database.delete(
                    "contacts",
                    "id<>? AND (locked=1 OR name=?)",
                    new String[]{
                            String.valueOf(securityId),
                            SECURITY_NAME
                    }
            );

            // Renumber personal contacts from Priority 2.
            int priority = 2;

            try (Cursor cursor = database.query(
                    "contacts",
                    new String[]{"id"},
                    "id<>?",
                    new String[]{String.valueOf(securityId)},
                    null,
                    null,
                    "priority ASC, id ASC"
            )) {

                while (cursor.moveToNext()) {

                    ContentValues values = new ContentValues();

                    values.put("priority", priority++);
                    values.put("locked", 0);

                    database.update(
                            "contacts",
                            values,
                            "id=?",
                            new String[]{
                                    String.valueOf(cursor.getLong(0))
                            }
                    );
                }
            }

            database.setTransactionSuccessful();

        } finally {
            database.endTransaction();
        }
    }

    // ============================================================
    // GET CONTACTS
    // ============================================================

    public List<Contact> getContacts() {

        ensureCampusSecurity();

        List<Contact> contacts = new ArrayList<>();

        try (Cursor cursor = getReadableDatabase().query(
                "contacts",
                null,
                null,
                null,
                null,
                null,
                "priority ASC, id ASC"
        )) {

            while (cursor.moveToNext()) {

                Contact contact = new Contact();

                contact.id = cursor.getLong(
                        cursor.getColumnIndexOrThrow("id"));

                contact.name = cursor.getString(
                        cursor.getColumnIndexOrThrow("name"));

                contact.phone = cursor.getString(
                        cursor.getColumnIndexOrThrow("phone"));

                contact.relationship = cursor.getString(
                        cursor.getColumnIndexOrThrow("relationship"));

                contact.priority = cursor.getInt(
                        cursor.getColumnIndexOrThrow("priority"));

                contact.locked = cursor.getInt(
                        cursor.getColumnIndexOrThrow("locked")) == 1;

                contacts.add(contact);
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to load contacts", e);
        }

        return contacts;
    }

    // ============================================================
    // ADD CONTACT
    // ============================================================

    public boolean addContact(
            String name,
            String phone,
            String relationship) {

        List<Contact> contacts = getContacts();

        if (contacts.size() >= MAX_CONTACTS) {
            return false;
        }

        if (name == null || name.trim().isEmpty()
                || SECURITY_NAME.equalsIgnoreCase(name.trim())) {
            return false;
        }

        ContentValues values = new ContentValues();

        values.put("name", name.trim());
        values.put("phone", phone);
        values.put("relationship", relationship);
        values.put("priority", contacts.size() + 1);
        values.put("locked", 0);

        return getWritableDatabase().insert(
                "contacts",
                null,
                values
        ) != -1;
    }

    // ============================================================
    // UPDATE CONTACT
    // ============================================================

    public void updateContact(
            long id,
            String name,
            String phone,
            String relationship) {

        if (name == null || name.trim().isEmpty()
                || SECURITY_NAME.equalsIgnoreCase(name.trim())) {
            return;
        }

        ContentValues values = new ContentValues();

        values.put("name", name.trim());
        values.put("phone", phone);
        values.put("relationship", relationship);

        getWritableDatabase().update(
                "contacts",
                values,
                "id=? AND locked=0",
                new String[]{String.valueOf(id)}
        );
    }

    // ============================================================
    // DELETE CONTACT
    // ============================================================

    public void deleteContact(long id) {

        getWritableDatabase().delete(
                "contacts",
                "id=? AND locked=0",
                new String[]{String.valueOf(id)}
        );

        // Recalculate priorities after deletion.
        getContacts();
    }

    // ============================================================
    // MOVE CONTACT PRIORITY
    // ============================================================

    public void moveContact(long id, int direction) {

        if (direction != -1 && direction != 1) {
            return;
        }

        List<Contact> contacts = getContacts();

        int currentIndex = -1;

        for (int i = 0; i < contacts.size(); i++) {

            if (contacts.get(i).id == id) {
                currentIndex = i;
                break;
            }
        }

        if (currentIndex < 0) {
            return;
        }

        int newIndex = currentIndex + direction;

        if (newIndex < 0
                || newIndex >= contacts.size()
                || contacts.get(currentIndex).locked
                || contacts.get(newIndex).locked) {
            return;
        }

        Collections.swap(
                contacts,
                currentIndex,
                newIndex
        );

        renumber(contacts);
    }

    // ============================================================
    // RENUMBER CONTACT PRIORITIES
    // ============================================================

    private void renumber(List<Contact> contacts) {

        SQLiteDatabase database = getWritableDatabase();

        database.beginTransaction();

        try {
            int personalPriority = 2;

            for (Contact contact : contacts) {

                ContentValues values = new ContentValues();

                if (contact.locked) {
                    values.put("priority", 1);
                } else {
                    values.put("priority", personalPriority++);
                }

                database.update(
                        "contacts",
                        values,
                        "id=?",
                        new String[]{
                                String.valueOf(contact.id)
                        }
                );
            }

            database.setTransactionSuccessful();

        } finally {
            database.endTransaction();
        }
    }
}

