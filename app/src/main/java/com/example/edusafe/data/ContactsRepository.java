package com.example.edusafe.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;


public class ContactsRepository {

    private static final String PREFS_NAME = "edusafe_contacts";
    private static final String KEY_CONTACTS = "contacts_json";
    private static final String KEY_SEED_VERSION = "seed_version";

    /** Raise this number to reset everyone's saved contacts to the defaults below. */
    private static final int SEED_VERSION = 2;

    private final SharedPreferences prefs;

    public ContactsRepository(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        seedDefaultsIfNeeded();
    }

    private void seedDefaultsIfNeeded() {
        boolean alreadySeeded = prefs.contains(KEY_CONTACTS)
                && prefs.getInt(KEY_SEED_VERSION, 1) >= SEED_VERSION;
        if (alreadySeeded) return;

        List<EmergencyContact> defaults = new ArrayList<>();
        defaults.add(new EmergencyContact("Campus Security", "0111112222", 1));
        defaults.add(new EmergencyContact("Parent / Guardian", "0831234507", 2));
        defaults.add(new EmergencyContact("Trusted Friend", "0769876543", 3));
        saveAll(defaults);
        prefs.edit().putInt(KEY_SEED_VERSION, SEED_VERSION).apply();
    }

    /** Returns all saved contacts, sorted by priority (1 first). */
    public List<EmergencyContact> getContacts() {
        List<com.example.edusafe.data.EmergencyContact> result = new ArrayList<>();
        String json = prefs.getString(KEY_CONTACTS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                result.add(new com.example.edusafe.data.EmergencyContact(
                        obj.getString("name"),
                        obj.getString("phone"),
                        obj.getInt("priority")
                ));
            }
        } catch (JSONException e) {
            // Corrupt or empty store — treat as no contacts rather than crash.
            return new ArrayList<>();
        }
        Collections.sort(result, Comparator.comparingInt(c -> c.priority));
        return result;
    }

    public void addContact(com.example.edusafe.data.EmergencyContact contact) {
        List<com.example.edusafe.data.EmergencyContact> current = getContacts();
        current.add(contact);
        saveAll(current);
    }

    public void removeContact(String name) {
        List<com.example.edusafe.data.EmergencyContact> current = getContacts();
        current.removeIf(c -> c.name.equals(name));
        saveAll(current);
    }

    private void saveAll(List<EmergencyContact> contacts) {
        JSONArray arr = new JSONArray();
        try {
            for (com.example.edusafe.data.EmergencyContact c : contacts) {
                JSONObject obj = new JSONObject();
                obj.put("name", c.name);
                obj.put("phone", c.phoneNumber);
                obj.put("priority", c.priority);
                arr.put(obj);
            }
        } catch (JSONException e) {
            throw new RuntimeException("Failed to serialize contacts", e);
        }
        prefs.edit().putString(KEY_CONTACTS, arr.toString()).apply();
    }
}
