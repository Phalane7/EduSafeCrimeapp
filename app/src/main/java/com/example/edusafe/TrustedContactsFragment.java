package com.example.edusafe;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.List;
import java.util.regex.Pattern;

public class TrustedContactsFragment extends Fragment {

    // South African phone numbers: 0821234567 or +27821234567.
    private static final Pattern PHONE = Pattern.compile("^(\\+27|0)\\d{9}$");
    private static final String OLD_PLACEHOLDER_NUMBER = "0111112222";

    private DatabaseHelper db;
    private LinearLayout listContainer;
    private TextView btnAdd, tvNote;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_contacts, container, false);
        db = DatabaseHelper.get(requireContext());
        listContainer = view.findViewById(R.id.listContainer);
        btnAdd = view.findViewById(R.id.btnAdd);
        tvNote = view.findViewById(R.id.tvNote);
        btnAdd.setOnClickListener(v -> showDialog(null));
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (getView() == null) return;
        listContainer.removeAllViews();

        final List<DatabaseHelper.Contact> contacts;
        try {
            contacts = db.getContacts();
        } catch (Exception e) {
            toast("Couldn't load your contacts. Please try again.");
            return;
        }

        for (int i = 0; i < contacts.size(); i++) {
            DatabaseHelper.Contact contact = contacts.get(i);
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_contact, listContainer, false);

            ((TextView) row.findViewById(R.id.tvAvatar)).setText(initials(contact.name));
            ((TextView) row.findViewById(R.id.tvName)).setText(contact.name);

            String number = contact.phone == null ? "" : contact.phone.trim();
            boolean configured = !number.isEmpty();
            ((TextView) row.findViewById(R.id.tvPhone))
                    .setText("Emergency: " + (configured ? number : "number not configured"));
            ((TextView) row.findViewById(R.id.tvPriority))
                    .setText("Priority " + contact.priority + (contact.locked ? " (fixed)" : ""));

            View up = row.findViewById(R.id.btnUp);
            View down = row.findViewById(R.id.btnDown);

            if (contact.locked) {
                up.setVisibility(View.GONE);
                down.setVisibility(View.GONE);
                row.setOnClickListener(v -> toast(configured
                        ? "Campus Security is a fixed contact and can't be changed here."
                        : "Your institution's Campus Security number has not been configured."));
            } else {
                // Priorities come from the database; a locked contact cannot be moved.
                boolean canMoveUp = i > 0 && !contacts.get(i - 1).locked;
                boolean canMoveDown = i < contacts.size() - 1 && !contacts.get(i + 1).locked;
                up.setVisibility(canMoveUp ? View.VISIBLE : View.INVISIBLE);
                down.setVisibility(canMoveDown ? View.VISIBLE : View.INVISIBLE);
                up.setOnClickListener(v -> move(contact.id, -1));
                down.setOnClickListener(v -> move(contact.id, 1));
                row.setContentDescription(contact.name + ", priority " + contact.priority
                        + ". Tap to edit or delete");
                row.setOnClickListener(v -> showOptions(contact));
            }
            listContainer.addView(row);
        }

        boolean full = contacts.size() >= DatabaseHelper.MAX_CONTACTS;
        btnAdd.setVisibility(full ? View.GONE : View.VISIBLE);
        tvNote.setText("You can save up to " + DatabaseHelper.MAX_CONTACTS
                + " contacts. You have " + contacts.size()
                + ". Use the arrows to change their priority. "
                + "SOS prepares a message for you to review and send; saving a contact does not send an alert.");
    }

    private void move(long contactId, int direction) {
        try {
            db.moveContact(contactId, direction);
            load();
        } catch (Exception e) {
            toast("Couldn't change the contact priority.");
        }
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        StringBuilder result = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && result.length() < 2) {
                result.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        return result.toString();
    }

    private void showOptions(DatabaseHelper.Contact contact) {
        if (contact.locked) return;
        new AlertDialog.Builder(requireContext())
                .setTitle(contact.name)
                .setItems(new String[]{"Edit contact", "Delete contact"}, (dialog, which) -> {
                    if (which == 0) showDialog(contact);
                    else confirmDelete(contact);
                })
                .show();
    }

    private void confirmDelete(DatabaseHelper.Contact contact) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Delete contact?")
                .setMessage(contact.name + " will be removed from your saved SOS contacts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        db.deleteContact(contact.id);
                        load();
                    } catch (Exception e) {
                        toast("Couldn't delete the contact. Please try again.");
                    }
                })
                .show();
    }

    private void showDialog(@Nullable DatabaseHelper.Contact existing) {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_contact, null);
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPhone = dialogView.findViewById(R.id.etPhone);
        RadioGroup rg = dialogView.findViewById(R.id.rgRel);

        if (existing != null) {
            if (existing.locked) return;
            etName.setText(existing.name);
            etPhone.setText(existing.phone);
            if ("Friend".equals(existing.relationship)) rg.check(R.id.rbFriend);
            else if ("Other".equals(existing.relationship)) rg.check(R.id.rbOther);
            else rg.check(R.id.rbGuardian);
        } else {
            rg.check(R.id.rbGuardian);
        }

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? "Add emergency contact" : "Edit contact")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                    String name = etName.getText().toString().trim();
                    String phone = etPhone.getText().toString().replaceAll("[\\s-]", "");
                    if (name.isEmpty()) {
                        etName.setError("Please enter a name.");
                        etName.requestFocus();
                        return;
                    }
                    if (!PHONE.matcher(phone).matches()) {
                        etPhone.setError("Enter a valid South African number, e.g. 082 123 4567.");
                        etPhone.requestFocus();
                        return;
                    }
                    if (OLD_PLACEHOLDER_NUMBER.equals(phone)) {
                        etPhone.setError("Please enter a real phone number.");
                        etPhone.requestFocus();
                        return;
                    }

                    int checkedId = rg.getCheckedRadioButtonId();
                    String relationship = checkedId == R.id.rbFriend ? "Friend"
                            : checkedId == R.id.rbOther ? "Other" : "Guardian";
                    try {
                        if (existing == null) {
                            if (!db.addContact(name, phone, relationship)) {
                                toast("Couldn't add the contact. Check the contact limit.");
                                return;
                            }
                        } else {
                            db.updateContact(existing.id, name, phone, relationship);
                        }
                        dialog.dismiss();
                        load();
                    } catch (Exception e) {
                        toast("Couldn't save the contact. Please try again.");
                    }
                }));
        dialog.show();
    }

    private void toast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }
}
