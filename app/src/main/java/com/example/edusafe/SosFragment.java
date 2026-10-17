package com.example.edusafe;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class SosFragment extends Fragment {
    private TextView tvContacts;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_sos, container, false);
        tvContacts = v.findViewById(R.id.tvContacts);
        v.findViewById(R.id.btnCall112).setOnClickListener(x -> confirmCall());
        v.findViewById(R.id.btnAlertContacts).setOnClickListener(x -> alertContacts());
        v.findViewById(R.id.btnFindHelp).setOnClickListener(x -> findNearbyHelp());
        refreshContacts();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContacts();
    }

    private void refreshContacts() {
        if (tvContacts == null || getContext() == null) return;
        int count = 0;
        for (DatabaseHelper.Contact contact : DatabaseHelper.get(requireContext()).getContacts()) {
            if (!contact.locked) count++;
        }
        tvContacts.setText(count == 0
                ? "No trusted contacts yet. Add people you trust in Profile before an emergency."
                : count + " trusted contact" + (count == 1 ? "" : "s") + " ready to alert.");
    }

    private void confirmCall() {
        if (!requireContext().getSharedPreferences("app_settings", 0).getBoolean("confirm_sos", true)) {
            openDialler();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Call emergency services?")
                .setMessage("Your phone's dialler will open with 112. Review the number and tap call.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open dialler", (dialog, which) -> openDialler())
                .show();
    }

    private void openDialler() {
        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
        } catch (ActivityNotFoundException e) {
            toast("Couldn't open the phone app. Call 112 from another phone.");
        }
    }

    private void alertContacts() {
        List<DatabaseHelper.Contact> contacts = new ArrayList<>();
        for (DatabaseHelper.Contact contact : DatabaseHelper.get(requireContext()).getContacts()) {
            // The locked Campus Security row ships with a setup placeholder; do not send to it.
            if (!contact.locked && contact.phone != null && !contact.phone.trim().isEmpty()) contacts.add(contact);
        }
        if (contacts.isEmpty()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("No trusted contacts")
                    .setMessage("Add a trusted contact in Profile, or call emergency services on 112.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        String[] recipients = new String[contacts.size()];
        List<String> names = new ArrayList<>();
        for (int i = 0; i < contacts.size(); i++) {
            recipients[i] = contacts.get(i).phone;
            names.add(contacts.get(i).name);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("Alert trusted contacts?")
                .setMessage("Open a text message addressed to " + TextUtils.join(", ", names)
                        + ". Review it and tap send. No message is sent automatically.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open messages", (dialog, which) -> openMessage(recipients))
                .show();
    }

    private void openMessage(String[] recipients) {
        String body = "I need help. Please contact me now. Add your current location before sending if you can.";
        Uri address = Uri.parse("smsto:" + TextUtils.join(";", recipients));
        Intent intent = new Intent(Intent.ACTION_SENDTO, address);
        intent.putExtra("sms_body", body);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            toast("Couldn't open a messaging app. Call 112 for emergency help.");
        }
    }

    private void findNearbyHelp() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("emergency services"))));
        } catch (ActivityNotFoundException e) {
            toast("No maps app is available. Call 112 for emergency help.");
        }
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}
